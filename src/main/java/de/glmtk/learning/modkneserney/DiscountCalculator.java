/*
 * Generalized Language Modeling Toolkit (GLMTK)
 *
 * Copyright (C) 2015 Lukas Schmelzeisen
 *
 * GLMTK is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * GLMTK is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GLMTK. If not, see <http://www.gnu.org/licenses/>.
 *
 * See the AUTHORS file for contributors.
 */

package de.glmtk.learning.modkneserney;

import static de.glmtk.Constants.MODEL_MODKNESERNEY;
import static de.glmtk.common.Output.OUTPUT;

import java.io.IOException;
import java.nio.file.Path;

import de.glmtk.Constants;
import de.glmtk.common.Config;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.common.Pattern;
import de.glmtk.common.Status;
import de.glmtk.counts.Discounts;
import de.glmtk.counts.NGramTimes;
import de.glmtk.files.DiscountWriter;
import de.glmtk.files.NGramTimesReader;
import de.glmtk.logging.Logger;
import de.glmtk.util.NioUtils;

// TODO: Filter patterns, so we only calculate discounts for patterns used in MKN.
public class DiscountCalculator {
    private static final Logger LOGGER = Logger.get(DiscountCalculator.class);

    @SuppressWarnings("unused")
    private Config config;

    protected String model;

    public DiscountCalculator(Config config) {
        this.config = config;
        model = MODEL_MODKNESERNEY;
    }

    public void run(Status status,
                    Path ngramTimesFile,
                    Path outputFile) throws IOException {
        OUTPUT.setPhase(Phase.CALCULATING_DISCOUNTS);

        if (status.areDiscountsCalculated(model)) {
            LOGGER.debug("Status reports discouts calculated, returning.");
            return;
        }

        Progress progress = OUTPUT.newProgress(NioUtils.calcNumberOfLines(ngramTimesFile));

        try (NGramTimesReader reader = new NGramTimesReader(ngramTimesFile,
                Constants.CHARSET);
                DiscountWriter writer = new DiscountWriter(outputFile,
                        Constants.CHARSET)) {
            while (reader.readLine() != null) {
                Pattern pattern = reader.getPattern();
                NGramTimes ngramTimes = reader.getNGramTimes();

                Discounts discount = calcDiscounts(ngramTimes);
                writer.append(pattern, discount);

                progress.increase(1);
            }
        }

        status.setDiscountsCalculated(model);
    }

    private Discounts calcDiscounts(NGramTimes n) {
        double y = (double) n.getOneCount()
                / (n.getOneCount() + n.getTwoCount());
        Discounts result = new Discounts(1.0f - 2.0f * y * n.getTwoCount()
                / n.getOneCount(), 2.0f - 3.0f * y * n.getThreeCount()
                / n.getTwoCount(), 3.0f - 4.0f * y * n.getFourCount()
                / n.getThreeCount());

        return result;
    }
}
