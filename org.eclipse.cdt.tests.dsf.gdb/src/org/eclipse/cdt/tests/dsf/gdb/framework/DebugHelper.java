package org.eclipse.cdt.tests.dsf.gdb.framework;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.cdt.debug.core.CDIDebugModel;
import org.eclipse.cdt.debug.core.model.ICBreakpointType;
import org.eclipse.cdt.debug.core.model.ICFunctionBreakpoint;
import org.eclipse.cdt.debug.core.model.ICLineBreakpoint;
import org.eclipse.cdt.debug.internal.core.breakpoints.CFunctionBreakpoint;
import org.eclipse.cdt.debug.internal.core.breakpoints.CLineBreakpoint;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

@SuppressWarnings("restriction")
public class DebugHelper {
	
	public static IResource getResource() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public static ICFunctionBreakpoint createFunctionBreakpoint(String filename, String function) throws CoreException {
		return CDIDebugModel.createFunctionBreakpoint(filename, getResource(), 0,
				function, -1, -1, -1, true, 0, "", true);
	}
	
	public static ICLineBreakpoint createLineBreakpoint(String filename, int linenum) throws CoreException {
		return CDIDebugModel.createLineBreakpoint(
				filename, getResource(),
				ICBreakpointType.REGULAR, linenum, true, 0, "", true);
	}
	
	private static List<IBreakpoint> getPlatformBreakpoints(Predicate<IBreakpoint> predicate) {
		return Arrays.asList(DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()).stream()
				.filter(predicate).collect(Collectors.toList());
	}
	
	public static List<IBreakpoint> getPlatformCFunctionBreakpoints() {
		return getPlatformBreakpoints(CFunctionBreakpoint.class::isInstance);
	}
	
	public static List<IBreakpoint> getPlatformCLineBreakpoints() {
		return getPlatformBreakpoints(CLineBreakpoint.class::isInstance);
	}
	
	public static List<IBreakpoint> getAllPlatformBreakpoints() {
		return Arrays.asList(DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()).stream().
		collect(Collectors.toList());
	}
	
  	public static void deleteAllPlatformBreakpoints() throws Exception {
  		IBreakpointManager bm = DebugPlugin.getDefault().getBreakpointManager();
  		for (IBreakpoint b : bm.getBreakpoints()) {
  			bm.removeBreakpoint(b, true);
  		}
  	}
  	
  	public static void toggleAllPlatformBreakpoints(Boolean enabled) throws CoreException {
  		List<IBreakpoint> list = getAllPlatformBreakpoints();
  		for (IBreakpoint bp : list) 
  		{
  			if (bp.isEnabled() != enabled) {
  				bp.setEnabled(enabled);
  			}
  		}
  	}
  	
  	public static void printAllBreakpointType() {
  		List<IBreakpoint> list = getAllPlatformBreakpoints();
  		for (IBreakpoint bp : list) {
  			System.err.println(bp.getClass().getName());
  		}
  	}
  	
	public static void removeAllPlatformBreakpoints() throws CoreException {
		IBreakpointManager manager = DebugPlugin.getDefault().getBreakpointManager();
		IBreakpoint[] breakpoints = manager.getBreakpoints();
		manager.removeBreakpoints(breakpoints, true);
	}
	
	public static void removeTeminatedLaunches() throws CoreException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunch[] launches = launchManager.getLaunches();
		for (ILaunch launch : launches) {
			if (!launch.isTerminated()) {
				fail("Something has gone wrong, there is an unterminated launch from a previous test!");
			}
		}
		if (launches.length > 0) {
			launchManager.removeLaunches(launches);
		}
	}
	
	public static void removeLaunchConfigurations() throws CoreException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfiguration[] launchConfigurations = launchManager.getLaunchConfigurations();
		for (ILaunchConfiguration launchConfiguration : launchConfigurations) {
			launchConfiguration.delete();
		}

		assertEquals("Failed to delete launch configurations", 0, launchManager.getLaunchConfigurations().length);
	}
	
	public static void showDebugPerspective() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench != null) {
			IPerspectiveRegistry preg = workbench
					.getPerspectiveRegistry();
			IPerspectiveDescriptor pd = preg.findPerspectiveWithId(
					"org.eclipse.debug.ui.DebugPerspective");
			if (pd != null) {

				IWorkbenchWindow window = workbench
						.getActiveWorkbenchWindow();
				try {
					workbench.showPerspective(pd.getId(), window);
				} catch (WorkbenchException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
