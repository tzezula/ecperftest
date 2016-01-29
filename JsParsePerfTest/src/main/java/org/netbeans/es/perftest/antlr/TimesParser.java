/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2015 Sun Microsystems, Inc.
 */
package org.netbeans.es.perftest.antlr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.netbeans.modules.javascript2.editor.parser6.ECMAScript6Parser;

/**
 *
 * @author Tomas Zezula
 */
public class TimesParser extends ECMAScript6Parser {
    private final Deque<RuleRun> activeRules;
    private final Map<RuleTime,RuleTime> histogram;

    public TimesParser(TokenStream input) {
        super(input);
        activeRules = new ArrayDeque<>();
        histogram = new HashMap<>();
    }

    @Override
    public void enterRule(ParserRuleContext localctx, int state, int ruleIndex) {
        activeRules.push(new RuleRun(ruleIndex, _input.LA(1), System.nanoTime()));
        super.enterRule(localctx, state, ruleIndex);
    }

    @Override
    public void exitRule() {
        super.exitRule();
        final RuleRun head = activeRules.pop();
        final RuleTime key = new RuleTime(head.ruleNo, head.input);
        RuleTime timeSoFar = histogram.get(key);
        if (timeSoFar == null) {
            timeSoFar = key;
        }
        final long selfTime = System.nanoTime() - head.startTime - head.normalization;
        timeSoFar.time += selfTime;
        histogram.put(key, timeSoFar);
        for (RuleRun t : activeRules) {
            t.normalization+=selfTime;
        }

    }

    public final Collection<RuleTime> getHistogram() {
        final List<RuleTime> l = new ArrayList<>(histogram.values());
        Collections.sort(l);
        Collections.reverse(l);
        return l;
    }

    private static final class RuleRun {
        final int ruleNo;
        final int input;
        final long startTime;
        long normalization;

        RuleRun(
                int ruleNo,
                int input,
                long startTime) {
            this.ruleNo = ruleNo;
            this.input = input;
            this.startTime = startTime;
        }
    }

    public static final class RuleTime implements Comparable<RuleTime>{
        final int ruleNo;
        final int input;
        long time;

        RuleTime(
            int ruleNo,
            int input) {
            this.ruleNo = ruleNo;
            this.input = input;
        }

        @Override
        public int hashCode() {
            int hc = ruleNo;
            hc = hc*31 + input;
            return hc;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof RuleTime)) {
                return false;
            }
            RuleTime other = (RuleTime) obj;
            return ruleNo == other.ruleNo && input == other.input;
        }

        @Override
        public int compareTo(RuleTime o) {
            return Long.compare(time, o.time);
        }

        String toString(Parser parser) {
            return String.format("%s\t\t%s\t\t->\t%dms",
                parser.getRuleNames()[ruleNo],
                parser.getVocabulary().getSymbolicName(input),
                time/1_000_000);
        }
    }

}
