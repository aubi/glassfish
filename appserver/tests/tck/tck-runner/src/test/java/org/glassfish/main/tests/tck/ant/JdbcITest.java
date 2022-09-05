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
public class JdbcITest {

    @Inject
    private TckRunner tck;

    @Test
    public void appClient() throws Exception {
        tck.start(Path.of("jdbc_appclient"));
    }


    @Test
    public void ejb() throws Exception {
        tck.start(Path.of("jdbc_ejb"));
    }


    @Test
    public void jsp() throws Exception {
        tck.start(Path.of("jdbc_jsp"));
    }


    @Test
    public void servlet() throws Exception {
        tck.start(Path.of("jdbc_servlet"));
    }
}
