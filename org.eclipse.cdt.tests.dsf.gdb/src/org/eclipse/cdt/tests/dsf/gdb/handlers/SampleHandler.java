package org.eclipse.cdt.tests.dsf.gdb.handlers;

import org.eclipse.cdt.tests.dsf.gdb.framework.LaunchGDB;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

public class SampleHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		MessageDialog.openInformation(
				window.getShell(),
				"Gdb",
				"Say Hello, Eclipse World");
		
		LaunchGDB test = new LaunchGDB();
		try {
			LaunchGDB.setGlobalPreferences();
			test.doGDBLaunch();
			//test.testFunctionBreakpointsAreIndependent0();
			//test.doAfterTest();
			//LaunchGDB.restoreGlobalPreferences();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}