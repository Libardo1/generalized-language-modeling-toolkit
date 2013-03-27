package de.typology.executables;

import java.io.File;
import java.io.IOException;

import de.typology.utils.Config;

public class WikiBuilder extends Builder {

	/**
	 * executes the following steps:
	 * <p>
	 * 1) parse and normalize wikipedia data
	 * <p>
	 * 2) split into training.txt, testing.txt, and learning.txt
	 * <p>
	 * 3) build index.txt
	 * <p>
	 * 4) build ngrams
	 * <p>
	 * 5) build typoedges
	 * 
	 * @author Martin Koerner
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		WikiBuilder wb = new WikiBuilder();

		File dir = new File(Config.get().wikiInputDirectory);
		String outputDirectory = Config.get().outputDirectory + "wiki/";
		new File(outputDirectory).mkdirs();
		for (File f : dir.listFiles()) {
			String wikiTyp = f.getName().split("-")[0];
			String outputPath = outputDirectory + wikiTyp + "/";
			new File(outputPath).mkdirs();
			wb.build(f.getAbsolutePath(), outputPath);
		}
	}
}
