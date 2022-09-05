/*
 * Copyright (c) 2022 Eclipse Foundation and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.main.tests.tck.ant;

import jakarta.inject.Inject;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * @author David Matejcek
 */
@ExtendWith(TckTestExtension.class)
public class EjbITest {

    @Inject
    private TckRunner tck;

    @Test
    public void ejb() throws Exception {
        tck.start(Path.of("ejb"));
    }


    @Test
    public void ejb30LiteAppException() throws Exception {
        tck.start(Path.of("ejb30", "lite", "appexception"));
    }


    @Test
    public void ejb30LiteAsync() throws Exception {
        tck.start(Path.of("ejb30", "lite", "async"));
    }


    @Test
    public void ejb30LiteBasic() throws Exception {
        tck.start(Path.of("ejb30", "lite", "basic"));
    }

    /**
     * Usual time: 7 minutes
     */
    @Test
    public void ejb30LiteEjbContext() throws Exception {
        tck.start(Path.of("ejb30", "lite", "ejbcontext"));
    }

    /**
     * Usual time: 7 minutes
     */
    @Test
    public void ejb30LiteEnvEntry() throws Exception {
        tck.start(Path.of("ejb30", "lite", "enventry"));
    }


    @Test
    public void ejb30LiteLookup() throws Exception {
        tck.start(Path.of("ejb30", "lite", "lookup"));
    }


    @Test
    public void ejb30LiteNaming() throws Exception {
        tck.start(Path.of("ejb30", "lite", "naming"));
    }


    @Test
    public void ejb30LiteNointerface() throws Exception {
        tck.start(Path.of("ejb30", "lite", "nointerface"));
    }


    @Test
    public void ejb30LitePackaging() throws Exception {
        tck.start(Path.of("ejb30", "lite", "packaging"));
    }


    @Test
    public void ejb30LiteSingleton() throws Exception {
        tck.start(Path.of("ejb30", "lite", "singleton"));
    }


    @Test
    public void ejb30LiteStatefulConcurrency() throws Exception {
        tck.start(Path.of("ejb30", "lite", "stateful", "concurrency"));
    }


    @Test
    public void ejb30LiteStatefulTimeout() throws Exception {
        tck.start(Path.of("ejb30", "lite", "stateful", "timeout"));
    }


    @Test
    public void ejb30LiteTx() throws Exception {
        tck.start(Path.of("ejb30", "lite", "tx"));
    }


    @Test
    public void ejb30LiteView() throws Exception {
        tck.start(Path.of("ejb30", "lite", "view"));
    }


    @Test
    public void ejb30LiteXmlOverride() throws Exception {
        tck.start(Path.of("ejb30", "lite", "xmloverride"));
    }


    @Test
    public void ejb30Assembly() throws Exception {
        tck.start(Path.of("ejb30", "assembly"));
    }


    @Test
    public void ejb30BB() throws Exception {
        tck.start(Path.of("ejb30", "bb"));
    }


    @Test
    public void ejb30Misc() throws Exception {
        tck.start(Path.of("ejb30", "misc"));
    }


    @Test
    public void ejb30Sec() throws Exception {
        tck.start(Path.of("ejb30", "sec"));
    }


    @Test
    public void ejb30Timer() throws Exception {
        tck.start(Path.of("ejb30", "timer"));
    }


    @Test
    public void ejb30Webservice() throws Exception {
        tck.start(Path.of("ejb30", "webservice"));
    }


    @Test
    public void ejb30Zombie() throws Exception {
        tck.start(Path.of("ejb30", "zombie"));
    }


    @Test
    public void ejb32() throws Exception {
        tck.start(Path.of("ejb32"));
    }
}
