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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.dfa.DFAState;
import org.netbeans.es.perftest.ParserOptions;
import org.netbeans.es.perftest.ParserImplementation;
import org.netbeans.modules.javascript2.editor.parser6.ECMAScript6Lexer;
import org.netbeans.modules.javascript2.editor.parser6.ECMAScript6Parser;

/**
 *
 * @author Tomas Zezula
 */
public class AntlrParser implements ParserImplementation {
    public static final String NAME = "antlr";  //NOI18N
    private static final String OPT_HISTO = "histo";    //NOI18N
    private static final String OPT_LEX = "lex";    //NOI18N
    private static final String OPT_ATNCFG_COUNT = "atncfg";    //NOI18N

    private boolean printHistogram = false;
    private boolean printAtnCfgCount = false;
    private boolean lex = false;
    private int printAtnCfgCountLimit = -1;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getOptions() {
        final Map m =  new TreeMap<>();
        m.put(OPT_HISTO, "prints a histogram for each parser rule");    //NOI18N
        m.put(OPT_LEX, "meassures only lexer");                         //NOI18N
        m.put(OPT_ATNCFG_COUNT,"prints ATNConfig count per decision");  //NOI18N
        return m;
    }

    @Override
    public void setUp(ParserOptions options) {
        printHistogram = lex = printAtnCfgCount = false;
        printAtnCfgCountLimit = -1;
        for (String option : options.getParserSpecificOptions()) {
            final String[] splitted = ParserOptions.splitParserArg(option);
            switch (splitted[0]) {
                case OPT_HISTO:
                    printHistogram = true;
                    break;
                case OPT_LEX:
                    lex = true;
                    break;
                case OPT_ATNCFG_COUNT:
                    printAtnCfgCount = true;
                    if (splitted.length > 1) {
                        printAtnCfgCountLimit = Integer.parseInt(splitted[1]);
                    }
                    break;
                default:
                    throw new IllegalArgumentException(option);
            }
        }
    }

    @Override
    public boolean parse(File file, ParserOptions options) throws IOException {
        final ErrorListener errorListener = new ErrorListener(
                options.getProgressWriter(),
                options.isPrintError());
        final ANTLRInputStream in = new ANTLRFileStream(file.getAbsolutePath());
        final ECMAScript6Lexer lexer = new ECMAScript6Lexer(in);
        lexer.addErrorListener(errorListener);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        if (lex) {
            //Lexer performance only
            tokens.fill();
            tokens.getTokens();
        } else {
            //Full parse performance
            ECMAScript6Parser parser = printHistogram ?
                    new TimesParser(tokens) :
                    new ECMAScript6Parser(tokens);
            parser.removeErrorListeners();
            parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            parser.setErrorHandler(new BailErrorStrategy());
            ECMAScript6Parser.ProgramContext program;
            try {
                program = parser.program();
            } catch (RuntimeException ex) {
                if ((ex instanceof RuntimeException) && (ex.getCause() instanceof RecognitionException)) {
                    tokens.reset();
                    parser.setErrorHandler(new DefaultErrorStrategy());
                    parser.addErrorListener(errorListener);
                    parser.getInterpreter().setPredictionMode(PredictionMode.LL);
                    program = parser.program();
                } else {
                    throw ex;
                }
            }
            if (printHistogram) {
                final PrintWriter pw = options.getProgressWriter();
                ((TimesParser)parser).getHistogram()
                        .stream()
                        .forEach((rt) -> {
                            pw.println(rt.toString(parser));
                        });
            }
        }
        return !errorListener.hasErrors();
    }

    @Override
    public void report(ParserOptions options) {
        if (printAtnCfgCount) {
            final PrintWriter rw = options.getReportWriter();
            rw.printf("%nATNConfig count per decision:%n"); //NOI18N
            final DFA[] dfas = ECMAScript6Parser._ATN.decisionToDFA;
            final int[][] atnCfgCountToDecision = new int[dfas.length][];
            for (int i=0; i<dfas.length; i++) {
                int atnCfgCount = 0;
                for (DFAState dfaState : dfas[i].states.keySet()) {
                    atnCfgCount+=dfaState.configs.size();
                }
                atnCfgCountToDecision[i] = new int[] {i, atnCfgCount};
            }
            Arrays.sort(
                    atnCfgCountToDecision,
                    (int[]a, int[]b) -> {
                        return a[1] > b[1] ? -1 : a[1] == b[1] ? 0 : 1;
                    }
            );
            Arrays.stream(atnCfgCountToDecision)
                    .limit(printAtnCfgCountLimit == -1 ?
                            atnCfgCountToDecision.length :
                            printAtnCfgCountLimit)
                    .forEach((a)->{
                        rw.printf("Decision: %d ATNConfigs: %d%n",  //NOI18N
                                a[0],
                                a[1]);
                        });
            rw.flush();
        }
    }
}
