package tde.debug;

import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.LineBreakpoint;

public class TestplanBreakpoint extends LineBreakpoint {

	private static final String BREAKPOINT_ID = "com.agilent.p9000.specs.tde.tplBreakpoint";

	public TestplanBreakpoint() {
		super();
	}
	
	public void addBreakpoint(final IResource resource,
			final String markerType, final Map<String, Object> attributes)
			throws CoreException {

		IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
			@Override
			public void run(IProgressMonitor monitor) throws CoreException {
				setMarker(resource.createMarker(markerType));
				ensureMarker().setAttributes(attributes);
				setPersisted(true);
				register();
			}
		};

		run(runnable);
	}

	@Override
	public String getModelIdentifier() {
		return BREAKPOINT_ID;
	}

	private void register() throws CoreException {
		DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(this);
	}

	private void run(IWorkspaceRunnable runnable) throws DebugException {
		try {
			ResourcesPlugin.getWorkspace().run(runnable, null);
		} catch (CoreException e) {
			throw new DebugException(e.getStatus());
		}
	}

}


