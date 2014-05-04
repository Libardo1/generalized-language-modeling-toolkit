package de.typology.splitting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import de.typology.Sequencer;
import de.typology.indexing.WordIndex;
import de.typology.indexing.WordIndexBuilder;

public class SequencerTest {

    File trainingFile = new File("testDataset/training.txt");

    File indexFile = new File("testDataset/index.txt");

    File sequencerOutputDirectory = new File("testDataset/sequencer/");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        WordIndexBuilder wordIndexer = new WordIndexBuilder();
        wordIndexer.buildIndex(Files.newInputStream(trainingFile.toPath()),
                Files.newOutputStream(indexFile.toPath()), 10, "<fs> <s> ",
                " </s>");
        if (sequencerOutputDirectory.exists()) {
            FileUtils.deleteDirectory(sequencerOutputDirectory);
        }
        sequencerOutputDirectory.mkdir();
    }

    @After
    public void tearDown() throws Exception {
        if (sequencerOutputDirectory.exists()) {
            FileUtils.deleteDirectory(sequencerOutputDirectory);
        }
        if (indexFile.exists()) {
            indexFile.delete();
        }
    }

    @Test
    public void squencing1Test() throws IOException {
        WordIndex wordIndex = new WordIndex(new FileInputStream(indexFile));
        boolean[] pattern = {
            true
        };

        try {
            InputStream inputStream = new FileInputStream(trainingFile);
            Sequencer sequencer =
                    new Sequencer(inputStream,
                            sequencerOutputDirectory.toPath(), wordIndex, null,
                            pattern, "<fs> <s> ", " </s>", false, true, "\t");

            sequencer.splitIntoFiles();

            // test file contents
            BufferedReader br8 =
                    new BufferedReader(new FileReader(
                            sequencerOutputDirectory.getAbsolutePath() + "/8"));
            for (int i = 0; i < 10; i++) {
                assertEquals("et\t1", br8.readLine());
            }
            assertNull(br8.readLine());
            br8.close();

            BufferedReader br2 =
                    new BufferedReader(new FileReader(
                            sequencerOutputDirectory.getAbsolutePath() + "/3"));
            for (int i = 0; i < 20; i++) {
                assertEquals("<s>\t1", br2.readLine());
            }
            assertNull(br2.readLine());
            br2.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void squencing1101Test() throws IOException {
        WordIndex wordIndex = new WordIndex(new FileInputStream(indexFile));
        boolean[] pattern = {
            true, true, false, true
        };

        try {
            InputStream inputStream = new FileInputStream(trainingFile);
            Sequencer sequencer =
                    new Sequencer(inputStream,
                            sequencerOutputDirectory.toPath(), wordIndex, null,
                            pattern, "<fs> <s> ", " </s>", false, true, "\t");
            sequencer.splitIntoFiles();

            // test file contents
            BufferedReader br0 =
                    new BufferedReader(new FileReader(
                            sequencerOutputDirectory.getAbsolutePath() + "/8"));
            for (int i = 0; i < 6; i++) {
                assertEquals("et justo dolores\t1", br0.readLine());
            }
            assertNull(br0.readLine());
            br0.close();

            BufferedReader br10 =
                    new BufferedReader(new FileReader(
                            sequencerOutputDirectory.getAbsolutePath() + "/3"));
            for (int i = 0; i < 6; i++) {
                assertEquals("<s> Lorem dolor\t1", br10.readLine());
            }
            assertEquals("<s> Lorem </s>\t1", br10.readLine());
            br10.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
