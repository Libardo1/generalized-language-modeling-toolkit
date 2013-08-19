package de.typology.parser;

import static de.typology.parser.Token.CLOSEDID;
import static de.typology.parser.Token.CLOSEDSQUAREDBRACKET;
import static de.typology.parser.Token.CLOSEDTEXT;
import static de.typology.parser.Token.CLOSEDTITLE;
import static de.typology.parser.Token.ID;
import static de.typology.parser.Token.LINK;
import static de.typology.parser.Token.OTHER;
import static de.typology.parser.Token.SQUAREDBRACKET;
import static de.typology.parser.Token.TEXT;
import static de.typology.parser.Token.TITLE;
import static de.typology.parser.Token.VERTICALBAR;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import de.typology.utils.IOHelper;

/**
 * @author Martin Koerner
 * 
 *         derived from
 *         http://101companies.org/index.php/101implementation:javaLexer
 * 
 */
public class WikipediaLinkExtractor {
	private WikipediaRecognizer recognizer;
	private Writer writer;
	private String head;
	private String lexeme;
	private String title;
	private String id;
	private String link;
	private String linkLabel;
	private Token label;
	private Token current;
	private int bracketCount;
	private int verticalBarCount;

	public WikipediaLinkExtractor(WikipediaRecognizer recognizer,
			String output, String head) throws FileNotFoundException {
		this.recognizer = recognizer;
		new File(output).getParentFile().mkdirs();
		this.writer = IOHelper.openWriteFile(output, 32 * 1024 * 1024);
		this.head = head;
	}

	public void extractLinks() throws IOException {
		while (this.recognizer.hasNext()) {
			this.read();
			if (this.current == TITLE) {
				this.title = new String();
				while (this.current != CLOSEDTITLE) {
					if (this.recognizer.hasNext()) {
						this.current = this.recognizer.next();
						this.lexeme = this.recognizer.getLexeme();
						if (this.current == CLOSEDTITLE) {
							break;
						} else {
							this.title += this.lexeme;
						}
					} else {
						throw new IllegalStateException();
					}

				}
			}
			if (this.current == TEXT) {
				while (this.current != CLOSEDTEXT) {
					this.label = null;
					this.read();

					// Recognize links
					if (this.current == SQUAREDBRACKET) {
						this.bracketCount = 1;
						this.verticalBarCount = 0;
						this.link = new String();
						this.linkLabel = new String();
						while (this.bracketCount != 0
								&& this.current != CLOSEDTEXT) {
							this.read();
							if (this.current == SQUAREDBRACKET) {
								this.bracketCount++;
							}
							if (this.current == CLOSEDSQUAREDBRACKET) {
								this.bracketCount--;
							}
							if (this.bracketCount > 2) {
								this.label = OTHER;
							}
							if (this.bracketCount == 2 && this.label != OTHER) {
								// inside a valid link
								this.label = LINK;
								if (this.current != SQUAREDBRACKET
										&& this.current != CLOSEDSQUAREDBRACKET
										&& this.current != VERTICALBAR) {
									this.linkLabel += this.lexeme;
								}
								if (this.current == VERTICALBAR) {
									this.verticalBarCount++;
									this.link = this.linkLabel;
									this.linkLabel = new String();
								}
							}
						}
						if (this.label == LINK && this.bracketCount == 0
								&& this.verticalBarCount < 2) {
							if (this.label == LINK) {
								if (this.verticalBarCount == 0) {
									// [[linkLabel]]
									this.write(this.head
											+ this.title.replaceAll("\\s", "_")
											+ "\t"
											+ this.linkLabel
											+ "\t"
											+ this.head
											+ this.linkLabel.replaceAll("\\s",
													"_") + "\n");
								} else {
									if (this.verticalBarCount == 1) {
										// [[link|linkLabel]]
										this.write(this.head
												+ this.title.replaceAll("\\s",
														"_")
												+ "\t"
												+ this.linkLabel
												+ "\t"
												+ this.head
												+ this.link.replaceAll("\\s",
														"_") + "\n");
									}
								}
							}
						}
					}
				}
				this.writer.flush();
			}
		}
		this.writer.close();
	}

	public void extractFiles() throws IOException {
		while (this.recognizer.hasNext()) {
			this.read();
			if (this.current == TITLE) {
				this.title = new String();
				while (this.current != CLOSEDTITLE) {
					if (this.recognizer.hasNext()) {
						this.current = this.recognizer.next();
						this.lexeme = this.recognizer.getLexeme();
						if (this.current == CLOSEDTITLE) {
							break;
						} else {
							this.title += this.lexeme;
						}
					} else {
						throw new IllegalStateException();
					}

				}
				while (this.current != ID) {
					this.read();
				}
				while (this.current != CLOSEDID) {
					if (this.recognizer.hasNext()) {
						this.current = this.recognizer.next();
						this.lexeme = this.recognizer.getLexeme();
						if (this.current == CLOSEDID) {
							break;
						} else {
							this.id = this.lexeme;
						}
					} else {
						throw new IllegalStateException();
					}

				}
			}
			if (this.current == TEXT) {
				ArrayList<String> fileList = new ArrayList<String>();
				while (this.current != CLOSEDTEXT) {
					this.read();

					// Recognize links
					if (this.current == SQUAREDBRACKET) {
						this.read();
						if (this.current == SQUAREDBRACKET) {
							this.read();
							if (this.lexeme.equals("Datei")) {
								this.read();
								String file = new String();
								if (this.lexeme.equals(":")) {
									this.read();
									while (!this.lexeme.equals("]")
											&& !this.lexeme.equals("|")) {
										file += this.lexeme;
										this.read();
									}
									fileList.add(file);
								}
							}
						}
					}
				}
				if (fileList.size() > 0) {
					String links = this.id + "\t" + this.title;
					for (String file : fileList) {
						links += "\t" + file;
					}
					links += "\n";
					this.write(links);
					this.writer.flush();
				}
			}
		}
		this.writer.close();
	}

	public void read() throws IOException {
		if (this.recognizer.hasNext()) {
			this.current = this.recognizer.next();
			this.lexeme = this.recognizer.getLexeme();
		} else {
			throw new IllegalStateException();
		}
	}

	public void write(String s) {
		try {
			this.writer.write(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
