package de.glmtk.pattern;

import static de.glmtk.pattern.PatternElem.CNT;
import static de.glmtk.pattern.PatternElem.DEL;
import static de.glmtk.pattern.PatternElem.POS;
import static de.glmtk.pattern.PatternElem.SKP;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Pattern implements Iterable<PatternElem>, Cloneable {

    private List<PatternElem> pattern;

    private String asString;

    public Pattern(
            List<PatternElem> pattern) {
        this.pattern = pattern;
        updateAsString();
    }

    public Pattern(
            String pattern) {
        // todo: doesn't error on PatternElem#fromString == null
        this.pattern = new ArrayList<PatternElem>(pattern.length());
        for (Character elem : pattern.toCharArray()) {
            this.pattern.add(PatternElem.fromString(elem.toString()));
        }
        asString = pattern;
    }

    private void updateAsString() {
        StringBuilder asStringBuilder = new StringBuilder();
        for (PatternElem elem : pattern) {
            asStringBuilder.append(elem);
        }
        asString = asStringBuilder.toString();
    }

    public int length() {
        return pattern.size();
    }

    public PatternElem get(int index) {
        return pattern.get(index);
    }

    public PatternElem getFirstNonSkp() {
        for (PatternElem elem : pattern) {
            if (!elem.equals(PatternElem.SKP)) {
                return elem;
            }
        }
        return PatternElem.SKP;
    }

    public void set(int index, PatternElem elem) {
        pattern.set(index, elem);
        updateAsString();
    }

    @Override
    public Pattern clone() {
        return new Pattern(asString);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other == null || !other.getClass().equals(Pattern.class)) {
            return false;
        }

        Pattern o = (Pattern) other;
        return asString.equals(o.asString);
    }

    @Override
    public int hashCode() {
        return asString.hashCode();
    }

    @Override
    public String toString() {
        return asString;
    }

    @Override
    public Iterator<PatternElem> iterator() {
        return pattern.iterator();
    }

    public boolean contains(PatternElem elem) {
        return contains(Arrays.asList(elem));
    }

    public boolean contains(Collection<PatternElem> elems) {
        for (PatternElem e : pattern) {
            for (PatternElem e2 : elems) {
                if (e.equals(e2)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsOnly(PatternElem elem) {
        return contains(Arrays.asList(elem));
    }

    public boolean containsOnly(Collection<PatternElem> elems) {
        outerLoop:
        for (PatternElem e : pattern) {
                for (PatternElem e2 : elems) {
                    if (e.equals(e2)) {
                        continue outerLoop;
                    }
                }
                return false;
            }
        return true;
    }

    public boolean isAbsolute() {
        return containsOnly(Arrays.asList(CNT, SKP, POS));
    }

    public int numElems(Collection<PatternElem> elems) {
        int result = 0;
        outerLoop:
            for (PatternElem e : pattern) {
                for (PatternElem e2 : elems) {
                    if (e.equals(e2)) {
                        ++result;
                        continue outerLoop;
                    }
                }
            }
        return result;
    }

    public Pattern replace(PatternElem target, PatternElem replacement) {
        Pattern newPattern = clone();
        for (int i = 0; i != newPattern.length(); ++i) {
            if (newPattern.get(i).equals(target)) {
                newPattern.set(i, replacement);
            }
        }
        return newPattern;
    }

    public Pattern replaceLast(PatternElem target, PatternElem replacement) {
        Pattern newPattern = clone();
        for (int i = newPattern.length() - 1; i != -1; --i) {
            if (newPattern.get(i).equals(target)) {
                newPattern.set(i, replacement);
                break;
            }
        }
        return newPattern;
    }

    public String apply(String[] words, String[] pos, int p) {
        StringBuilder result = new StringBuilder();

        boolean first = true;
        int i = 0;
        for (PatternElem elem : pattern) {
            if (elem != DEL) {
                if (!first) {
                    result.append(' ');
                }
                first = false;
            }

            result.append(elem.apply(words[p + i], pos[p + i]));

            ++i;
        }

        return result.toString();
    }

    public String apply(String[] words) {
        StringBuilder result = new StringBuilder();

        boolean first = true;
        int i = 0;
        for (PatternElem elem : pattern) {
            if (elem != PatternElem.DEL) {
                if (!first) {
                    result.append(' ');
                }
                first = false;
            }

            result.append(elem.apply(words[i]));

            ++i;
        }

        return result.toString();
    }

    public Pattern getContinuationSource() {
        Pattern sourcePattern = clone();
        for (int i = length() - 1; i != -1; --i) {
            PatternElem elem = get(i);
            if (elem.equals(PatternElem.WSKP)) {
                sourcePattern.set(i, PatternElem.CNT);
                return sourcePattern;
            } else if (elem.equals(PatternElem.PSKP)) {
                sourcePattern.set(i, PatternElem.POS);
                return sourcePattern;
            }
        }
        throw new IllegalArgumentException("Pattern '" + this
                + "' is not a continuation pattern.");
    }

    // Static //////////////////////////////////////////////////////////////////

    public static int getModelLength(Set<Pattern> patterns) {
        int modelLength = 0;
        for (Pattern pattern : patterns) {
            if (pattern.length() > modelLength) {
                modelLength = pattern.length();
            }
        }
        return modelLength;
    }

    public static Set<Pattern> getCombinations(
            int modelLength,
            List<PatternElem> elems) {
        Set<Pattern> patterns = new HashSet<Pattern>();

        for (int i = 1; i != modelLength + 1; ++i) {
            for (int j = 0; j != pow(elems.size(), i); ++j) {
                List<PatternElem> pattern = new ArrayList<PatternElem>(i);
                int n = j;
                for (int k = 0; k != i; ++k) {
                    pattern.add(elems.get(n % elems.size()));
                    n /= elems.size();
                }
                patterns.add(new Pattern(pattern));
            }
        }

        return patterns;
    }

    private static int pow(int base, int power) {
        int result = 1;
        for (int i = 0; i != power; ++i) {
            result *= base;
        }
        return result;
    }

    public static Set<Pattern> replaceTargetWithElems(
            Set<Pattern> inputPatterns,
            PatternElem target,
            List<PatternElem> elems) throws IOException {
        Set<Pattern> patterns = new HashSet<Pattern>();

        for (Pattern pattern : inputPatterns) {
            if (pattern.contains(target)) {
                for (PatternElem elem : elems) {
                    patterns.add(pattern.replace(target, elem));
                }
            }
        }

        return patterns;
    }

}
