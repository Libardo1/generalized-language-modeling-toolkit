package de.glmtk.counting;

import static de.glmtk.common.Config.CONFIG;
import static de.glmtk.common.Output.OUTPUT;
import static de.glmtk.util.PrintUtils.humanReadableByteCount;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.glmtk.Constants;
import de.glmtk.common.Counter;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.common.Pattern;
import de.glmtk.common.Status;
import de.glmtk.exceptions.FileFormatException;
import de.glmtk.util.ExceptionUtils;
import de.glmtk.util.NioUtils;
import de.glmtk.util.ObjectUtils;
import de.glmtk.util.StatisticalNumberHelper;
import de.glmtk.util.ThreadUtils;

public enum Merger {
    MERGER;

    private static class SequenceCountReader implements Closeable, AutoCloseable {
        public static final Comparator<SequenceCountReader> COMPARATOR = new Comparator<Merger.SequenceCountReader>() {
            @Override
            public int compare(SequenceCountReader lhs,
                               SequenceCountReader rhs) {
                if (lhs == rhs)
                    return 0;
                else if (lhs == null)
                    return 1;
                else if (rhs == null)
                    return -1;
                else
                    return ObjectUtils.compare(lhs.sequence, rhs.sequence);
            }
        };

        private Path file;
        private BufferedReader reader;
        private int lineNo;
        private String sequence;
        private Counter counter;

        public SequenceCountReader(Path file,
                                   Charset charset,
                                   int sz) throws IOException {
            this.file = file;
            reader = NioUtils.newBufferedReader(file, charset, sz);
            lineNo = -1;
            nextLine();
        }

        public void nextLine() throws IOException {
            String line = reader.readLine();
            ++lineNo;
            if (line == null) {
                sequence = null;
                counter = null;
            } else {
                counter = new Counter();
                try {
                    sequence = Counter.getSequenceAndCounter(line, counter);
                } catch (IllegalArgumentException e) {
                    throw new FileFormatException(line, lineNo, file, "chunk",
                            e.getMessage());
                }
            }
        }

        public String getSequence() {
            return sequence;
        }

        public Counter getCounter() {
            return counter;
        }

        public boolean isEmpty() {
            return sequence == null;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private static final Logger LOGGER = LogManager.getFormatterLogger(Merger.class);

    private class Thread implements Callable<Object> {
        @Override
        public Object call() throws InterruptedException, IOException {
            while (!patternQueue.isEmpty()) {
                Pattern pattern = patternQueue.poll(Constants.QUEUE_TIMEOUT,
                        TimeUnit.MILLISECONDS);
                if (pattern == null) {
                    LOGGER.trace("Thread Idle.");
                    StatisticalNumberHelper.count("Merger#Thread idle");
                    continue;
                }

                mergePattern(pattern);

                synchronized (progress) {
                    progress.increase(1);
                }
            }

            LOGGER.debug("Thread finished.");
            return null;
        }

        private void mergePattern(Pattern pattern) throws IOException {
            Path patternDir = chunkedDir.resolve(pattern.toString());

            Set<String> chunksForPattern;
            while ((chunksForPattern = status.getChunksForPattern(continuation,
                    pattern)).size() != 1) {
                int numParallelChunks = Math.min(numParallelReaders,
                        chunksForPattern.size());

                Set<String> chunksToMerge = new LinkedHashSet<>();
                Iterator<String> iter = chunksForPattern.iterator();
                for (int i = 0; i != numParallelChunks; ++i)
                    chunksToMerge.add(iter.next());

                Path mergeFile = patternDir.resolve("merge"
                        + getMergeCounter(chunksForPattern));
                LOGGER.debug("Merging pattern %s:\t%s -> %s.", pattern,
                        chunksToMerge, mergeFile.getFileName());
                mergeChunksToFile(patternDir, chunksToMerge, mergeFile);

                synchronized (status) {
                    try {
                        status.performMergeForChunks(continuation, pattern,
                                chunksToMerge,
                                mergeFile.getFileName().toString());
                    } catch (IOException e) {
                        // Updating status did not work, we continue in the hope
                        // it works next time.
                        LOGGER.warn("Unable to write status, continuing: "
                                + ExceptionUtils.getStackTrace(e));
                    }

                    for (String chunk : chunksToMerge)
                        Files.deleteIfExists(patternDir.resolve(chunk));
                }
            }

            Path src = patternDir.resolve(chunksForPattern.iterator().next());
            Path dest = countedDir.resolve(pattern.toString());

            synchronized (status) {
                LOGGER.debug("Finishing pattern %s:\t%s\t -> %s.", pattern,
                        src, dest);
                Files.deleteIfExists(dest);
                Files.move(src, dest);
                status.finishMerge(continuation, pattern);

                if (NioUtils.isDirEmpty(patternDir))
                    Files.deleteIfExists(patternDir);
            }
        }

        private int getMergeCounter(Set<String> chunksForPattern) {
            int maxMergeNr = 0;
            for (String chunk : chunksForPattern)
                if (chunk.startsWith("merge")) {
                    int mergeNr = Character.getNumericValue(chunk.charAt("merge".length()));
                    if (maxMergeNr < mergeNr)
                        maxMergeNr = mergeNr;
                }
            return maxMergeNr + 1;
        }

        private void mergeChunksToFile(Path patternDir,
                                       Set<String> chunksToMerge,
                                       Path mergeFile) throws IOException {
            Files.deleteIfExists(mergeFile);

            try (BufferedWriter writer = NioUtils.newBufferedWriter(mergeFile,
                    Constants.CHARSET, (int) writerMemory)) {
                PriorityQueue<SequenceCountReader> readerQueue = new PriorityQueue<>(
                        chunksToMerge.size(), SequenceCountReader.COMPARATOR);
                int memoryPerReader = (int) (readerMemory / chunksToMerge.size());
                for (String chunk : chunksToMerge) {
                    Path chunkFile = patternDir.resolve(chunk);
                    int memory = (int) Math.min(Files.size(chunkFile),
                            memoryPerReader);
                    @SuppressWarnings("resource")
                    SequenceCountReader sequenceCountReader = new SequenceCountReader(
                            chunkFile, Constants.CHARSET, memory);
                    readerQueue.add(sequenceCountReader);
                }

                String lastSequence = null;
                Counter aggregationCounter = null;
                while (!readerQueue.isEmpty()) {
                    @SuppressWarnings("resource")
                    SequenceCountReader reader = readerQueue.poll();
                    if (reader.isEmpty()) {
                        reader.close();
                        continue;
                    }

                    String sequence = reader.getSequence();
                    Counter counter = reader.getCounter();

                    if (sequence.equals(lastSequence))
                        aggregationCounter.add(counter);
                    else {
                        if (lastSequence != null) {
                            writer.append(lastSequence).append('\t');
                            if (continuation)
                                writer.append(aggregationCounter.toString());
                            else
                                writer.append(Long.toString(aggregationCounter.getOnePlusCount()));
                            writer.append('\n');
                        }
                        lastSequence = sequence;
                        aggregationCounter = counter;
                    }
                    reader.nextLine();
                    readerQueue.add(reader);
                }
            }
        }
    }

    private int numParallelReaders = 10;
    private Progress progress;
    private boolean continuation;
    private Status status;
    private Path chunkedDir;
    private Path countedDir;
    private BlockingQueue<Pattern> patternQueue;
    private long readerMemory;
    private long writerMemory;

    public void mergeAbsolute(Status status,
                              Set<Pattern> patterns,
                              Path chunkedDir,
                              Path countedDir) throws Exception {
        OUTPUT.setPhase(Phase.ABSOLUTE_MERGING);
        merge(false, status, patterns, chunkedDir, countedDir);
    }

    public void mergeContinuation(Status status,
                                  Set<Pattern> patterns,
                                  Path chunkedDir,
                                  Path countedDir) throws Exception {
        OUTPUT.setPhase(Phase.CONTINUATION_MERGING);
        merge(true, status, patterns, chunkedDir, countedDir);
    }

    private void merge(boolean continuation,
                       Status status,
                       Set<Pattern> patterns,
                       Path chunkedDir,
                       Path countedDir) throws Exception {
        LOGGER.debug("patterns = %s", patterns);
        if (patterns.isEmpty())
            return;

        Files.createDirectories(countedDir);

        this.continuation = continuation;
        this.status = status;
        this.chunkedDir = chunkedDir;
        this.countedDir = countedDir;
        patternQueue = new LinkedBlockingDeque<>(patterns);
        calculateMemory();

        List<Callable<Object>> threads = new LinkedList<>();
        for (int i = 0; i != CONFIG.getNumberOfThreads(); ++i)
            threads.add(new Thread());

        progress = new Progress(patterns.size());
        ThreadUtils.executeThreads(CONFIG.getNumberOfThreads(), threads);

        if (NioUtils.isDirEmpty(chunkedDir))
            Files.deleteIfExists(chunkedDir);
    }

    private void calculateMemory() {
        readerMemory = CONFIG.getReaderMemory();
        writerMemory = CONFIG.getWriterMemory();

        LOGGER.debug("readerMemory = %s", humanReadableByteCount(readerMemory));
        LOGGER.debug("writerMemory = %s", humanReadableByteCount(writerMemory));
    }
}
