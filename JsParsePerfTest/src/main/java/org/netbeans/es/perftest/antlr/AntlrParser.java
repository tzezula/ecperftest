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
import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.atn.PredictionMode;
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

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, String> getOptions() {
        final Map m =  new TreeMap<>();
        m.put(OPT_HISTO, "prints a histogram for each parser rule");    //NOI18N
        m.put(OPT_LEX, "meassures only lexer");                         //NOI18N
        return m;
    }

    @Override
    public void parse(File file, ParserOptions options) throws IOException {
        boolean printHistogram = false;
        boolean lex = false;
        for (String option : options.getParserSpecificOptions()) {
            switch (option) {
                case OPT_HISTO:
                    printHistogram = true;
                    break;
                case OPT_LEX:
                    lex = true;
                    break;
                default:
                    throw new IllegalArgumentException(option);
            }
        }
        final ANTLRInputStream in = new ANTLRFileStream(file.getAbsolutePath());
        final ECMAScript6Lexer lexer = new ECMAScript6Lexer(in);
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
                    if (options.isPrintError()) {
                        parser.addErrorListener(ConsoleErrorListener.INSTANCE);
                    }
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
    }
}
