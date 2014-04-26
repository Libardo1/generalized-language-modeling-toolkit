package de.typology.splitter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.typology.indexes.WordIndex;
import de.typology.patterns.PatternTransformer;

public class SmoothingSplitter {

    private File absoluteDirectory;

    private File continuationDirectory;

    private File indexFile;

    private String delimiter;

    private boolean deleteTempFiles;

    private Logger logger = LogManager.getLogger(this.getClass().getName());

    private ExecutorService executorService;

    private Comparator<boolean[]> patternComparator =
            new Comparator<boolean[]>() {

                @Override
                public int compare(boolean[] pattern1, boolean[] pattern2) {
                    return PatternTransformer.getStringPattern(pattern2)
                            .compareTo(
                                    PatternTransformer
                                            .getStringPattern(pattern1));
                }
            };

    public SmoothingSplitter(
            File absoluteDirectory,
            File continuationDirectory,
            File indexFile,
            String delimiter,
            boolean deleteTempFiles) {
        this.absoluteDirectory = absoluteDirectory;
        this.continuationDirectory = continuationDirectory;
        continuationDirectory.mkdir();
        this.indexFile = indexFile;
        this.delimiter = delimiter;
        this.deleteTempFiles = deleteTempFiles;
    }

    public void split(ArrayList<boolean[]> patterns, int cores)
            throws IOException {
        // read Index
        logger.info("read word index: " + indexFile.getAbsolutePath());
        WordIndex wordIndex = new WordIndex(indexFile);
        // initialize executerService
        // int cores = Runtime.getRuntime().availableProcessors();

        SortedMap<boolean[], boolean[]> continuationMap =
                filterContinuationMap(getContinuationMap(patterns));

        HashSet<boolean[]> finishedPatterns = new HashSet<boolean[]>();

        while (finishedPatterns.size() < continuationMap.size()) {
            ArrayList<boolean[]> currentPatterns = new ArrayList<boolean[]>();
            executorService = Executors.newFixedThreadPool(cores);

            for (Entry<boolean[], boolean[]> entry : continuationMap.entrySet()) {
                // list for storing patterns that are currently computed

                if (!finishedPatterns.contains(entry.getKey())) {
                    if (!PatternTransformer.getStringPattern(entry.getValue())
                            .contains("0")) {
                        // read absolute files
                        currentPatterns.add(entry.getKey());
                        logger.info("build continuation for "
                                + PatternTransformer.getStringPattern(entry
                                        .getKey())
                                + " from absolute "
                                + PatternTransformer.getStringPattern(entry
                                        .getValue()));

                        String inputPatternLabel =
                                PatternTransformer.getStringPattern(entry
                                        .getValue());
                        boolean[] outputPattern =
                                PatternTransformer
                                        .getBooleanPattern(PatternTransformer
                                                .getStringPattern(
                                                        entry.getKey())
                                                .replaceAll("0", ""));
                        String outputPatternLabel =
                                PatternTransformer.getStringPattern(
                                        entry.getKey()).replaceAll("0", "_");

                        File currentAbsoluteInputDirectory =
                                new File(absoluteDirectory.getAbsolutePath()
                                        + "/" + inputPatternLabel);

                        logger.debug("inputPattern: "
                                + PatternTransformer.getStringPattern(entry
                                        .getValue()));
                        logger.debug("inputPatternLabel: " + inputPatternLabel);
                        logger.debug("outputPattern: "
                                + PatternTransformer
                                        .getStringPattern(outputPattern));
                        logger.debug("newPatternLabel: " + outputPatternLabel);
                        logger.debug("patternForModifier: "
                                + PatternTransformer.getStringPattern(entry
                                        .getKey()));

                        splitType(currentAbsoluteInputDirectory,
                                continuationDirectory, outputPattern,
                                outputPatternLabel, entry.getKey(), wordIndex,
                                true, true);
                    } else {
                        if (finishedPatterns.contains(entry.getValue())) {
                            // read continuation files
                            currentPatterns.add(entry.getKey());
                            logger.info("build continuation for "
                                    + PatternTransformer.getStringPattern(entry
                                            .getKey())
                                    + " from continuation "
                                    + PatternTransformer.getStringPattern(entry
                                            .getValue()));

                            String inputPatternLabel =
                                    PatternTransformer.getStringPattern(
                                            entry.getValue()).replaceAll("0",
                                            "_");
                            boolean[] outputPattern =
                                    PatternTransformer
                                            .getBooleanPattern(PatternTransformer
                                                    .getStringPattern(
                                                            entry.getKey())
                                                    .replaceAll("0", ""));
                            String outputPatternLabel =
                                    PatternTransformer.getStringPattern(
                                            entry.getKey())
                                            .replaceAll("0", "_");

                            File currentContinuationInputDirectory =
                                    new File(
                                            continuationDirectory
                                                    .getAbsolutePath()
                                                    + "/"
                                                    + inputPatternLabel);

                            // build patternForModifier
                            boolean[] patternForModifier =
                                    new boolean[Integer
                                            .bitCount(PatternTransformer
                                                    .getIntPattern(entry
                                                            .getValue()))];
                            System.out.println(outputPatternLabel + "<--"
                                    + inputPatternLabel + " "
                                    + patternForModifier.length);
                            int patternPointer = 0;
                            for (int i = 0; i < entry.getValue().length; i++) {
                                if (entry.getKey()[i] && entry.getValue()[i]) {
                                    patternForModifier[patternPointer] = true;
                                    patternPointer++;
                                } else {
                                    if (!entry.getKey()[i]
                                            && entry.getValue()[i]) {
                                        patternForModifier[patternPointer] =
                                                false;
                                        patternPointer++;
                                    }
                                }
                            }

                            logger.debug("inputPattern: "
                                    + PatternTransformer.getStringPattern(entry
                                            .getValue()));
                            logger.debug("inputPatternLabel: "
                                    + inputPatternLabel);
                            logger.debug("outputPattern: "
                                    + PatternTransformer
                                            .getStringPattern(outputPattern));
                            logger.debug("newPatternLabel: "
                                    + outputPatternLabel);
                            logger.debug("patternForModifier: "
                                    + PatternTransformer
                                            .getStringPattern(patternForModifier));

                            splitType(currentContinuationInputDirectory,
                                    continuationDirectory, outputPattern,
                                    outputPatternLabel, patternForModifier,
                                    wordIndex, false, true);

                        }
                    }
                }
            }
            executorService.shutdown();
            logger.info("end of this round of calculation");
            try {
                executorService.awaitTermination(Long.MAX_VALUE,
                        TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            // add currently computed patterns to finishedPatterns
            for (boolean[] currentPattern : currentPatterns) {
                finishedPatterns.add(currentPattern);
            }
        }

    }

    private void splitType(
            File currentInputDirectory,
            File outputDirectory,
            boolean[] newPattern,
            String newPatternLabel,
            boolean[] patternForModifier,
            WordIndex wordIndex,
            boolean setCountToOne,
            boolean additionalCounts) {
        PipedInputStream pipedInputStream =
                new PipedInputStream(100 * 8 * 1024);

        if (Integer.bitCount(PatternTransformer.getIntPattern(newPattern)) == 0) {
            LineCounterTask lineCountTask =
                    new LineCounterTask(pipedInputStream, outputDirectory,
                            newPatternLabel, delimiter, setCountToOne,
                            additionalCounts);
            executorService.execute(lineCountTask);
        } else {
            // don't add tags here
            SplitterTask splitterTask =
                    new SplitterTask(pipedInputStream, outputDirectory,
                            wordIndex, newPattern, newPatternLabel, delimiter,
                            0, deleteTempFiles, "", "", true, false,
                            additionalCounts);
            executorService.execute(splitterTask);
        }

        try {
            OutputStream pipedOutputStream =
                    new PipedOutputStream(pipedInputStream);
            SequenceModifier sequenceModifier =
                    new SequenceModifier(currentInputDirectory,
                            pipedOutputStream, delimiter, patternForModifier,
                            true, setCountToOne);
            executorService.execute(sequenceModifier);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private SortedMap<boolean[], boolean[]> filterContinuationMap(
            SortedMap<boolean[], boolean[]> continuationMap) {
        SortedMap<boolean[], boolean[]> newContinuationMap =
                new TreeMap<boolean[], boolean[]>(patternComparator);
        for (Entry<boolean[], boolean[]> entry : continuationMap.entrySet()) {
            if (PatternTransformer.getStringPattern(entry.getKey()).equals(
                    PatternTransformer.getStringPattern(entry.getValue()))) {
                continue;
            }
            boolean[] currentPattern = entry.getKey();
            if (currentPattern.length > 2) {
                if (!currentPattern[0] && !currentPattern[1]) {
                    continue;
                }
            }
            newContinuationMap.put(entry.getKey(), entry.getValue());

        }
        return newContinuationMap;
    }

    private SortedMap<boolean[], boolean[]> getContinuationMap(
            ArrayList<boolean[]> patterns) {
        SortedMap<boolean[], boolean[]> continuationMap =
                new TreeMap<boolean[], boolean[]>(patternComparator);

        for (boolean[] inputPattern : patterns) {
            addPatterns(continuationMap, inputPattern, inputPattern, 0);
        }
        return continuationMap;
    }

    private void addPatterns(
            SortedMap<boolean[], boolean[]> continuationMap,
            boolean[] pattern,
            boolean[] oldPattern,
            int position) {
        if (position < pattern.length) {
            boolean[] newPattern = pattern.clone();
            newPattern[position] = false;
            continuationMap.put(newPattern, pattern);
            continuationMap.put(pattern, oldPattern);
            addPatterns(continuationMap, newPattern, pattern, position + 1);
            addPatterns(continuationMap, pattern, oldPattern, position + 1);
        }
    }

}
