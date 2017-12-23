package jnachos.kern;

import com.sun.security.auth.module.JndiLoginModule;
import jnachos.machine.Machine;

/**
 * Created by mrunal on 21/9/17.
 */
public class ForkedProcessHandler implements VoidFunctionPtr {
    @Override
    public void call(Object pArg) {

        JNachos.getCurrentProcess().restoreUserState();
        System.out.println("Inside Child Process PID : " + JNachos.getCurrentProcess().getProcessId());
        JNachos.getCurrentProcess().getSpace().restoreState();
        Machine.run();

    }
}
