/*
 * Copyright (c) 2022, 2023 Eclipse Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.main.itest.tools;

import jakarta.ws.rs.client.Client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.admin.rest.client.ClientWrapper;
import org.glassfish.main.itest.tools.asadmin.Asadmin;
import org.glassfish.main.itest.tools.asadmin.AsadminResult;
import org.glassfish.main.itest.tools.asadmin.AsadminResultMatcher;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This class represents GlassFish installation outside test environment.
 * <p>
 * Ensures that the domain in executed before first test started, and that the domain stops
 * after tests are finished.
 *
 * @author David Matejcek
 */
public class GlassFishTestEnvironment {
    private static final Logger LOG = Logger.getLogger(GlassFishTestEnvironment.class.getName());

    private static final File BASEDIR = detectBasedir();
    private static final File GF_ROOT = resolveGlassFishRoot();

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admintest";

    private static final File ASADMIN = findAsadmin();
    private static final File PASSWORD_FILE_FOR_UPDATE = findPasswordFile("password_update.txt");
    private static final File PASSWORD_FILE = findPasswordFile("password.txt");


    static {
        LOG.log(Level.INFO, "Using basedir: {0}", BASEDIR);
        LOG.log(Level.INFO, "Expected GlassFish directory: {0}", GF_ROOT);
        changePassword();
        Thread hook = new Thread(() -> {
            getAsadmin().exec(10_000, "stop-domain", "--kill", "--force");
        });
        Runtime.getRuntime().addShutdownHook(hook);
        assertThat(getAsadmin().exec(30_000, "start-domain", "--debug"), AsadminResultMatcher.asadminOK());
    }

    /**
     * @return {@link Asadmin} command api for tests.
     */
    public static Asadmin getAsadmin() {
        return new Asadmin(ASADMIN, ADMIN_USER, PASSWORD_FILE);
    }


    /**
     * @return project's target directory.
     */
    public static File getTargetDirectory() {
        return new File(BASEDIR, "target");
    }


    /**
     * @return domain1 directory absolute path
     */
    public static Path getDomain1Directory() {
        return GF_ROOT.toPath().resolve(Paths.get("domains", "domain1"));
    }


    /**
     * Creates a {@link Client} instance for the domain administrator.
     * Caller is responsible for closing.
     *
     * @return new {@link Client} instance
     */
    public static ClientWrapper createClient() {
        return new ClientWrapper(new HashMap<String, String>(), ADMIN_USER, ADMIN_PASSWORD);
    }


    /**
     * Creates a {@link HttpURLConnection} for the admin administrator.
     *
     * @param context - part of the url behind the <code>http://localhost:4848</code>
     * @return a new disconnected {@link HttpURLConnection}.
     * @throws IOException
     */
    public static HttpURLConnection openConnection(final String context) throws IOException {
        final HttpURLConnection connection = openConnection(4848, context);
        connection.setAuthenticator(new DasAuthenticator());
        return connection;
    }


    /**
     * Creates an unencrypted {@link HttpURLConnection} for the gine port and context.
     *
     * @param port
     * @param context - part of the url behind the <code>http://localhost:[port]</code>
     * @return a new disconnected {@link HttpURLConnection}.
     * @throws IOException
     */
    public static HttpURLConnection openConnection(final int port, final String context) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + context)
            .openConnection();
        connection.setRequestProperty("X-Requested-By", "JUnit5Test");
        return connection;
    }


    /**
     * This will delete the jobs.xml file
     */
    public static void deleteJobsFile() {
        Path path = GF_ROOT.toPath().resolve(Paths.get("domains", "domain1", "config", "jobs.xml"));
        LOG.log(Level.CONFIG, "Deleting: " + path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Useful for a heuristic inside Eclipse and other environments.
     *
     * @return Absolute path to the glassfish directory.
     */
    private static File detectBasedir() {
        final String basedir = System.getProperty("basedir");
        if (basedir != null) {
            return new File(basedir);
        }
        final File target = new File("target");
        if (target.exists()) {
            return target.getAbsoluteFile().getParentFile();
        }
        return new File(".").getAbsoluteFile().getParentFile();
    }


    private static File resolveGlassFishRoot() {
        final File gfDir = BASEDIR.toPath().resolve(Path.of("target", "glassfish7", "glassfish")).toFile();
        if (gfDir == null || !gfDir.exists()) {
            throw new IllegalStateException("The expected GlassFish home directory doesn't exist: " + gfDir);
        }
        return gfDir;
    }


    private static File findAsadmin() {
        return new File(GF_ROOT, isWindows() ? "bin/asadmin.bat" : "bin/asadmin");
    }


    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }


    private static File findPasswordFile(final String filename) {
        File output = new File(getTargetDirectory(), filename);
        if (output.exists()) {
            return output;
        }
        try {
            final Enumeration<URL> urls = Asadmin.class.getClassLoader().getResources(filename);
            if (urls == null || !urls.hasMoreElements()) {
                throw new IllegalStateException(filename + " not found");
            }
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream input = url.openStream()) {
                    Files.copy(input, output.toPath());
                }
                return output;
            }
            throw new IllegalStateException(filename + " not found");
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }


    private static void changePassword() {
        final Asadmin asadmin = new Asadmin(ASADMIN, ADMIN_USER, PASSWORD_FILE_FOR_UPDATE);
        final AsadminResult result = asadmin.exec(5_000, "change-admin-password");
        if (result.isError()) {
            // probably changed by previous execution without maven clean
            System.out.println("Admin password NOT changed.");
        } else {
            System.out.println("Admin password changed.");
        }
    }

    private static class DasAuthenticator extends Authenticator {

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(ADMIN_USER, ADMIN_PASSWORD.toCharArray());
        }
    }
}
