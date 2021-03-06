/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.rahas;

import org.apache.rahas.impl.util.AxiomParserPool;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.parse.XMLParserException;

/**
 * Rampart specific SAML bootstrap class. Here we set parser pool to
 * axiom specific one.
 */
public class RampartSAMLBootstrap extends DefaultBootstrap {
    protected RampartSAMLBootstrap() {
        super();
    }

    public static synchronized void bootstrap() throws ConfigurationException {
        initializeXMLSecurity();

        initializeXMLTooling();

        initializeArtifactBuilderFactories();

        initializeGlobalSecurityConfiguration();

        initializeParserPool();

        initializeESAPI();

        initializeHttpClient();
    }

    protected static void initializeParserPool() throws ConfigurationException {

        AxiomParserPool pp = new AxiomParserPool();
        pp.setMaxPoolSize(50);
        try {
            pp.initialize();
        } catch (XMLParserException e) {
            throw new ConfigurationException("Error initializing axiom based parser pool", e);
        }
        Configuration.setParserPool(pp);

    }
}
