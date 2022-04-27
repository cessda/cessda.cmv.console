/*
 * Copyright © 2017-2021 CESSDA ERIC (support@cessda.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.cessda.cmv.console;

import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;
// TODO - configure this from external sources
enum DDIVersion {
    DDI_3_2(buildContext("ddi:instance:3_2"), "//ddi:DDIInstance/s:StudyUnit/r:Citation/r:InternationalIdentifier/r:IdentifierContent", new SchemaValidator("/schemas/lifecycle/instance.xsd")),
    DDI_2_5(buildContext("ddi:codebook:2_5"), "//ddi:codeBook//ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo", new SchemaValidator("/schemas/codebook/codebook.xsd")),
    NESSTAR(buildContext("http://www.icpsr.umich.edu/DDI"), "//ddi:codeBook/stdyDscr/citation/titlStmt/IDNo", new SchemaValidator("/schemas/nesstar/Version1-2-2.xsd"));

    private final NamespaceContext namespaceContext;
    private final String pidXPath;
    private final SchemaValidator schemaValidator;

    DDIVersion(NamespaceContext namespaceContext, String pidXPath, SchemaValidator schemaValidator) {
        this.namespaceContext = namespaceContext;
        this.pidXPath = pidXPath;
        this.schemaValidator = schemaValidator;
    }

    private static NamespaceContext buildContext(String namespaceURI) {
        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "ddi".equals(prefix) ? namespaceURI : null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        };
    }

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    public String getXPath() {
        return pidXPath;
    }

    public SchemaValidator getSchemaValidator() {
        return schemaValidator;
    }
}
