package de.typology.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class WordIndexTest {
	File inputFile = new File("testDataset/training.txt");
	File indexFile = new File("testDataset/index.txt");

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		if (this.indexFile.exists()) {
			this.indexFile.delete();
		}
		WordIndexer wi = new WordIndexer();
		wi.buildIndex(this.inputFile, this.indexFile, 10, "<fs> <s> ", " </s>");
	}

	@After
	public void tearDown() throws Exception {
		if (this.indexFile.exists()) {
			this.indexFile.delete();
		}
	}

	@Test
	public void rankTest() {
		WordIndex wi = new WordIndex(this.indexFile);
		assertEquals(8, wi.rank("et"));
		assertEquals(3, wi.rank("A"));
		assertEquals(4, wi.rank("Z"));
		assertEquals(11, wi.rank("tempora"));
		assertEquals(11, wi.rank("z"));

		for (String word : wi) {
			assertTrue(word.length() > 0);
		}
	}

	@Test
	public void iteratorTest() {
		WordIndex wi = new WordIndex(this.indexFile);

		for (String word : wi) {
			assertTrue(word.length() > 0);
		}
	}

}
