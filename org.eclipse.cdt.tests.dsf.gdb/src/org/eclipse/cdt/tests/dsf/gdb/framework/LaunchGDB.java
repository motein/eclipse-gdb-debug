/*******************************************************************************
 * Copyright (c) 2007, 2016 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson			  - Initial Implementation
 *     Simon Marchi (Ericsson) - Add and use runningOnWindows().
 *******************************************************************************/
package org.eclipse.cdt.tests.dsf.gdb.framework;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.dsf.gdb.IGDBLaunchConfigurationConstants;
import org.eclipse.cdt.dsf.gdb.internal.GdbDebugOptions;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.gdb.launching.LaunchUtils;
import org.eclipse.cdt.dsf.mi.service.command.events.MIStoppedEvent;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.dsf.service.DsfSession.SessionStartedListener;
import org.eclipse.cdt.tests.dsf.gdb.launching.TestsPlugin;
import org.eclipse.cdt.tests.dsf.gdb.tests.ITestConstants;
import org.eclipse.cdt.utils.spawner.ProcessFactory;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.junit.Assert;
import org.junit.Assume;

@SuppressWarnings("restriction")
public class LaunchGDB {
	
	//region Constants
	public final static String DEFAULT_VERSION_STRING = "default"; // Use the default GDB instlled on this machine
	// Path to .exe or source file
	protected static final String EXEC_PATH = "E:/workspace/gdb-debug/org.eclipse.cdt.tests.dsf.gdb/data/launch/bin/";
	protected static final String SOURCE_PATH = "E:/workspace/gdb-debug/org.eclipse.cdt.tests.dsf.gdb/data/launch/src/";
	
	public static final String ATTR_DEBUG_SERVER_NAME = TestsPlugin.PLUGIN_ID + ".DEBUG_SERVER_NAME";
	private static final String DEFAULT_EXEC_NAME = "GDBMIGenericTestApp.exe";
	
	protected static final String GDB_NOT_FOUND = "not found";
	
	protected static final String DEBUG_SERVER_NAME = "gdbserver";
	protected static final String HOST_NAME = "localhost";
	protected static final String PORT_NUM = "9999";
	//endregion

	//region Members
    private static GdbLaunch fLaunch;
    
	private Map<String, Object> launchAttributes; // The set of attributes used for the launch
	private static Map<String, Object> globalLaunchAttributes = new HashMap<String, Object>(); // A set of global launch attributes

	private ILaunchConfiguration fLaunchConfiguration; // The launch configuration generated from the launch attributes
	
	private static Process gdbserverProc;
	
	private MIStoppedEvent fInitialStoppedEvent; // The MI event associated with the breakpoint at main()
	private static boolean fgStatusHandlersEnabled = true;
	
	private static Map<String, String> gdbCache = new HashMap<>(); // Preserve GDB name and version
	protected static String globalVersion;
	
	private HashMap<String, Integer> fTagLocations = new HashMap<>();
	
	// Provides the possibility to override the Debug Services factory and
	// override specific service(s)
	private static ServiceFactoriesManager fTestDebugServiceFactoriesMgr = new ServiceFactoriesManager();
	//endregion Members

    //region Public Functions
	//region Launch Functions
 	public static void setGlobalPreferences() {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(DebugPlugin.getUniqueIdentifier());
		// Disable status handlers
		fgStatusHandlersEnabled = Platform.getPreferencesService().getBoolean(DebugPlugin.getUniqueIdentifier(), IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, true, null);
		node.putBoolean(IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, false);
 	}

	public static void restoreGlobalPreferences() {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(DebugPlugin.getUniqueIdentifier());
		node.putBoolean(IInternalDebugCoreConstants.PREF_ENABLE_STATUS_HANDLERS, fgStatusHandlersEnabled);
	}
	
	public void doGDBLaunch() throws Exception {
		DebugHelper.removeTeminatedLaunches();
		DebugHelper.removeAllPlatformBreakpoints();
		setLaunchAttributes();
		doLaunch();
	}
	
	public void terminateGDBLaunch() throws Exception {
		if (fLaunch != null) {
			fLaunch.terminate();
			assertLaunchTerminates();
			fLaunch = null;
		}
		DebugHelper.removeAllPlatformBreakpoints();
	}
	//endregion Launch Functions
	
	//region Getter & Setter Functions
	public GdbLaunch getGDBLaunch() {
		return fLaunch;
	}
	
	public ILaunchConfiguration getLaunchConfiguration() {
		return fLaunchConfiguration;
	}

    public void setLaunchAttribute(String key, Object value) {
    	launchAttributes.put(key, value);
    }
    
    public Object getLaunchAttribute(String key) {
    	return launchAttributes.get(key);
    }

    public static void setGlobalLaunchAttribute(String key, Object value) {
    	globalLaunchAttributes.put(key, value);
    }
    
    public static Object getGlobalLaunchAttribite(String key){
    	return globalLaunchAttributes.get(key);
    }
    
    public synchronized MIStoppedEvent getInitialStoppedEvent() {
    	return fInitialStoppedEvent;
    }
    
	/**
	 * @return A Test Debug Service Factories manager which allow individual tests to register
	 * a specific service factory which can then provide mocked/extended instances of Test Services
	 */
	public static ServiceFactoriesManager getServiceFactoriesManager() {
		return fTestDebugServiceFactoriesMgr;
	}
	
	/**
	 * Sets the name of the gdb and gdbserver programs into the launch
	 * configuration used by the test class.
	 *
	 * <p>
	 * Leaf subclasses are specific to a particular version of GDB and must call
	 * this from their "@BeforeClass" static method so that we end up invoking
	 * the appropriate gdb.
	 *
	 * @param version
	 *            string that contains the major and minor version number, e.g.,
	 *            "6.8", special constant "default" represent default gdb on the box (called as "gdb")
	 */
 	public static void setGdbProgramNamesLaunchAttributes(String version) {
 		globalVersion = version;
 		setGlobalLaunchAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME, getProgramPath("gdb", version));
 		setGlobalLaunchAttribute(ATTR_DEBUG_SERVER_NAME, getProgramPath("gdbserver", version));
 	}
 	
	public static String getProgramPath(String main, String version) {
		// See bugzilla 303811 for why we have to append ".exe" on Windows
		boolean isWindows = runningOnWindows();
		String gdbPath = System.getProperty("cdt.tests.dsf.gdb.path");
		String fileExtension = isWindows ? ".exe" : "";
		String versionPostfix = (!version.equals(DEFAULT_VERSION_STRING)) ? "." + version : "";
		String debugName = main + versionPostfix + fileExtension;
		if (gdbPath != null) {
			debugName = gdbPath + "/" + debugName;
		}
		return debugName;
	}
	//endregion Getter & Setter Functions
    
    //region Remove Functions
    public void removeLaunchAttribute(String key) {
    	launchAttributes.remove(key);
    }

    public static void removeGlobalLaunchAttribute(String key) {
   		globalLaunchAttributes.remove(key);
    }
	
	/**
	 * Make sure we are starting with a clean/known state. That means no
	 * existing launches.
	 */
	
	/**
	 * Make sure we are starting with a clean/known state. That means no
	 * existing launch configurations.
	 * 
	 * XXX: Bugs 512180 and 501906, limit this call to only those test that
	 * really need a clean state. This does not remove the race condition, but
	 * does improve it somewhat.
	 */

    //endregion Remove Functions
    
	//region Other Supported Functions
    public boolean isRemoteSession() {
		return launchAttributes.get(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE)
	              .equals(IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE);
    }
	
    /**
     * Clear our knowledge of line tags. Must be called before
     * resolveLineTagLocations in {@link Intermittent} tests.
     * <p>
     * This is a workaround for Bug 508642. This may not seem necessary, since
     * the fTagLocations field is not static and a new instance of the test
     * class is created for each test. However, when a test marked as
     * {@link Intermittent} fails, the class instance is re-used, so the content
     * of the failed try leaks in the new try.
     */
    public void clearLineTags() {
        fTagLocations.clear();
    }
	
 	/**
 	 * This method will verify that the GDB binary is available, and if it is not, the test will
 	 * be ignored.  This method should be called by a SuiteGdb that specifies a specific GDB version.
 	 */
	public static void ignoreIfGDBMissing() {
		String gdb = (String) globalLaunchAttributes.get(IGDBLaunchConfigurationConstants.ATTR_DEBUG_NAME);
		String version = getGdbVersion(gdb);
		// If we cannot run GDB, just ignore the test case.
		Assume.assumeFalse("GDB cannot be run " + gdb, version == GDB_NOT_FOUND);
	}
	
 	public static boolean supportsNonStop() {
 		return !(runningOnWindows() || runningOnMac());
 	}
 	//endregion Other Supported Functions
 	
    //endregion Public Functions
    
    //region Protected Functions
	/**
	 * Validate that the gdb version launched is the one that was targeted.
	 * Will fail the test if the versions don't match.
	 * 
	 * @param launch The launch in which we can find the gdb version
	 */
	protected void validateGdbVersion(GdbLaunch launch) throws Exception {};
	
	protected void setLaunchAttributes() {
    	// Clear all launch attributes before launching
    	launchAttributes = new HashMap<String, Object>();

    	// Set the debug program
   		launchAttributes.put(ICDTLaunchConfigurationConstants.ATTR_PROGRAM_NAME, EXEC_PATH + DEFAULT_EXEC_NAME);

   		// Set it stop at the main function
		launchAttributes.put(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_STOP_AT_MAIN, true);
		launchAttributes.put(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_STOP_AT_MAIN_SYMBOL, ICDTLaunchConfigurationConstants.DEBUGGER_STOP_AT_MAIN_SYMBOL_DEFAULT);
		launchAttributes.put(IGDBLaunchConfigurationConstants.ATTR_GDB_INIT, ".gdbinit");
		// Run Debug mode while launching
    	launchAttributes.put(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE, ICDTLaunchConfigurationConstants.DEBUGGER_MODE_RUN );

		// Set these up in case we will be running remote clients.  They will be ignored if we don't
    	launchAttributes.put(ATTR_DEBUG_SERVER_NAME, DEBUG_SERVER_NAME);
    	launchAttributes.put(IGDBLaunchConfigurationConstants.ATTR_REMOTE_TCP, true);
    	launchAttributes.put(IGDBLaunchConfigurationConstants.ATTR_HOST, HOST_NAME);
    	launchAttributes.put(IGDBLaunchConfigurationConstants.ATTR_PORT, PORT_NUM);
    	launchAttributes.put(ITestConstants.LAUNCH_GDB_SERVER, true);
 
    	// Set the global launch attributes
    	launchAttributes.putAll(globalLaunchAttributes);
    }

	/**
	 * Given a set of tags (strings) to find in sourceFile, populate the
	 * fTagLocations map with the line numbers where they are found.
	 *
	 * @param sourceName The path of the source file, relative to {@link #SOURCE_PATH}.
	 * @param tags Strings to find in sourceFile.
	 * @throws IOException If sourceFile is not found or can't be read.
	 * @throws RuntimeException If one or more tags are not found in sourceFile.
	 */
	protected void resolveLineTagLocations(String sourceName,
			String... tags) throws IOException {
		try (BufferedReader reader =
				new BufferedReader(new FileReader(SOURCE_PATH + sourceName))) {
			Set<String> tagsToFind = new HashSet<>(Arrays.asList(tags));
			String line;

			for (int lineNumber = 1; (line = reader.readLine()) != null; lineNumber++) {
				for (String tag : tagsToFind) {
					if (line.contains(tag)) {
						if (fTagLocations.containsKey(tag)) {
							throw new RuntimeException("Tag " + tag
									+ " was found twice in " + sourceName);
						}
						fTagLocations.put(tag, lineNumber);
						tagsToFind.remove(tag);
						break;
					}
				}
			}
			/* Make sure all tags have been found */
			if (!tagsToFind.isEmpty()) {
				throw new RuntimeException(
						"Tags " + tagsToFind + " were not found in " + sourceName);
			}
		}
	}

	/**
	 * Get the source line number that contains the specified tag. In order to
	 * get an interesting result, {@link #resolveLineTagLocations} must be
	 * called prior to calling this function.
	 *
	 * @param tag Tag for which to get the source line.
	 * @return The line number corresponding to tag.
	 * @throws NoSuchElementException if the tag does not exist.
	 */
	protected int getLineForTag(String tag) {
		if (!fTagLocations.containsKey(tag)) {
			throw new NoSuchElementException("tag " + tag);
		}

		return fTagLocations.get(tag);
	}

    /**
     * Launch GDB.  The launch attributes must have been set already.
     */
 	protected void doLaunch() throws Exception {
		launchGdbServer();

 		ILaunchManager launchMgr = DebugPlugin.getDefault().getLaunchManager();
 		// The extension point needs to be extended.
 		ILaunchConfigurationType lcType = launchMgr.getLaunchConfigurationType("org.eclipse.cdt.tests.dsf.gdb.TestLaunch");
 		assert lcType != null;

 		ILaunchConfigurationWorkingCopy lcWorkingCopy = lcType.newInstance(
 				null,
 				launchMgr.generateLaunchConfigurationName("Test Launch")); //$NON-NLS-1$
 		assert lcWorkingCopy != null;
 		lcWorkingCopy.setAttributes(launchAttributes);

 		fLaunchConfiguration = lcWorkingCopy.doSave();
 		fLaunch = doLaunchInner(); // Perform the actual launch here

 		validateGdbVersion(fLaunch);

 		// If we started a gdbserver add it to the launch to make sure it is killed at the end
 		if (gdbserverProc != null) {
            DebugPlugin.newProcess(fLaunch, gdbserverProc, "gdbserver");
 		}

 		// Now initialize our SyncUtility, since we have the launcher
 		SyncUtil.initialize(fLaunch.getSession());
	}

 	/**
 	 * Perform the actual launch. This is normally called by {@link #doLaunch()}, however
 	 * it can be called repeatedly after an initial doLaunch sets up the environment. Doing
 	 * so allows multiple launches on the same launch configuration to be created. When this
 	 * method is called directly, the returned launch is not tracked and it is up to the
 	 * individual test to cleanup the launch. If the launch is not cleaned up, subsequent
 	 * tests will fail due to checks in {@link #doGDBLaunch()} that verify state is clean
 	 * and no launches are currently running.
 	 * 
 	 * This method is blocking until the breakpoint at main in the program is reached.
 	 * 
 	 * @return the new launch created
 	 */
	protected GdbLaunch doLaunchInner() throws Exception {
		assertNotNull("The launch configuration has not been created. Call doLaunch first.", fLaunchConfiguration);
		
 		boolean postMortemLaunch = launchAttributes.get(ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE)
                .equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_CORE);

 		SessionEventListener sessionEventListener = new SessionEventListener(fLaunchConfiguration);
 		SessionStartedListener sessionStartedListener = new SessionStartedListener() {
			@Override
			public void sessionStarted(DsfSession session) {
				sessionEventListener.setSession(session);
				session.addServiceEventListener(sessionEventListener, null);
			}
		};

		// Launch the debug session. The session-started listener will be called
		// before the launch() call returns (unless, of course, there was a
		// problem launching and no session is created).
 		DsfSession.addSessionStartedListener(sessionStartedListener);
 		GdbLaunch launch = (GdbLaunch)fLaunchConfiguration.launch(ILaunchManager.DEBUG_MODE, new NullProgressMonitor());
 		if (!GdbDebugOptions.DEBUG) {
 			// Now that we have started the launch we can print the real GDB version
 			// but not if DEBUG is on since we get the version anyway in that case.
 			GdbDebugOptions.trace(String.format(" Launched gdb %s.\n", launch.getGDBVersion()));
 		}

 		DsfSession.removeSessionStartedListener(sessionStartedListener);

 		try {

 	 		// If we haven't hit main() yet,
 	 		// wait for the program to hit the breakpoint at main() before
 			// proceeding. All tests assume that stable initial state. Two
 			// seconds is plenty; we typically get to that state in a few
 			// hundred milliseconds with the tiny test programs we use.
 			if (!postMortemLaunch) {
 				sessionEventListener.waitUntilTargetSuspended();
 	 		}

 	 		// This should be a given if the above check passes
 	 		if (!postMortemLaunch) {
 	 			synchronized(this) {
 	 				MIStoppedEvent initialStoppedEvent = sessionEventListener.getInitialStoppedEvent();
 	 				Assert.assertNotNull(initialStoppedEvent);
					if (fInitialStoppedEvent == null) {
						// On the very first launch we do, save the initial stopped event
						// XXX: If someone writes a test with an additional launch
						// that needs this info, they should resolve this return value then
						fInitialStoppedEvent = initialStoppedEvent;
					}
 	 			}
 	 		}
 	 		
 		} catch (Exception e) {
 			try {
 				launch.terminate();
 				assertLaunchTerminates(launch);
 			} catch (Exception inner) {
 				e.addSuppressed(inner);
 			}
 			throw e;
 		}
		
		return launch;
	}
	
	/**
	 * Assert that the launch terminates. Callers should have already
	 * terminated the launch in some way.
	 */
	protected void assertLaunchTerminates() throws Exception {
		GdbLaunch launch = fLaunch;
		assertLaunchTerminates(launch);
	}

	protected void assertLaunchTerminates(GdbLaunch launch) throws InterruptedException {
		if (launch != null) {
			// Give a few seconds to allow the launch to terminate
			int waitCount = 100;
			while (!launch.isTerminated() && !launch.getDsfExecutor().isShutdown() && --waitCount > 0) {
				Thread.sleep(TestsPlugin.massageTimeout(100));
			}
			assertTrue("Launch failed to terminate before timeout", launch.isTerminated());
		}
	}
	
	/**
	 * Wait until the given callable returns true, must be within timeout millis.
	 */
	protected void waitUntil(String message, Callable<Boolean> callable,  long millis) throws Exception {
		long endTime = System.currentTimeMillis() + millis;
		while (!callable.call() && System.currentTimeMillis() < endTime) {
			Thread.sleep(100);
		}
		assertTrue(message, callable.call());
	}

	/**
	 * Wait until the given callable returns true, must be within default timeout.
	 */
	protected void waitUntil(String message, Callable<Boolean> callable) throws Exception {
		waitUntil(message, callable, TestsPlugin.massageTimeout(2000));
	}
	
 	protected static boolean runningOnWindows() {
 		return Platform.getOS().equals(Platform.OS_WIN32);
 	}

 	protected static boolean runningOnMac() {
 		return Platform.getOS().equals(Platform.OS_MACOSX);
 	}
 	
	protected static String getGdbVersion(String gdb) {
		try {
			// See if we can find GDB by actually running it.
			String version = gdbCache.get(gdb);
			if (version == null) {
				version = doReadGdbVersion(gdb);
				gdbCache.put(gdb, version);
			}
			return version;
		} catch (IOException e) {
			gdbCache.put(gdb, GDB_NOT_FOUND);
			return GDB_NOT_FOUND;
		}
	}

	protected static String doReadGdbVersion(String gdb) throws IOException {
		Process process = ProcessFactory.getFactory().exec(gdb + " --version");
		try {
			String streamOutput;
			try (BufferedReader buffer = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				streamOutput = buffer.lines().collect(Collectors.joining("\n"));
			}
			String gdbVersion = LaunchUtils.getGDBVersionFromText(streamOutput);
			return gdbVersion;
		} finally {
			try {
				process.getOutputStream().close();
				process.getErrorStream().close();
				process.destroy();
			} catch (IOException e) {
				// ignore these
			}
		}
	}
 	
 	/**
 	 * This method start gdbserver on the localhost.
 	 * If the user specified a different host, things won't work.
 	 */
 	private void launchGdbServer() {
 		// First check if we should not launch gdbserver even for a remote session
 		if (launchAttributes.get(ITestConstants.LAUNCH_GDB_SERVER).equals(false)) {
 			if (GdbDebugOptions.DEBUG)
 				GdbDebugOptions.trace("Forcing to not start gdbserver for this launch\n");
 			
 			return;
 		}

 		if (isRemoteSession()) {
 			if (launchAttributes.get(IGDBLaunchConfigurationConstants.ATTR_REMOTE_TCP).equals(Boolean.TRUE)) {
 				String server = (String)launchAttributes.get(ATTR_DEBUG_SERVER_NAME);
 				String port = (String)launchAttributes.get(IGDBLaunchConfigurationConstants.ATTR_PORT);
 				String program = (String)launchAttributes.get(ICDTLaunchConfigurationConstants.ATTR_PROGRAM_NAME);
 				String commandLine = server + " :" + port + " " + program;
 				try {
 					if (GdbDebugOptions.DEBUG) GdbDebugOptions.trace("Starting gdbserver with command: " + commandLine + "\n");

 					gdbserverProc = ProcessFactory.getFactory().exec(commandLine);
                    Reader r = new InputStreamReader(gdbserverProc.getErrorStream());
                    BufferedReader reader = new BufferedReader(r);
                    String line;
                    while ((line = reader.readLine()) != null) {
                    	if(GdbDebugOptions.DEBUG)
                			GdbDebugOptions.trace(line + "\n");
                    	
                        line = line.trim();
                        if (line.startsWith("Listening on port"))
                            break;
                    }
 				} catch (Exception e) {
 					GdbDebugOptions.trace("Error while launching command: " + commandLine + "\n");
 					e.printStackTrace();
 					assert false;
 				}
 			}
 		}
	}
    //endregion Protected Functions
}
