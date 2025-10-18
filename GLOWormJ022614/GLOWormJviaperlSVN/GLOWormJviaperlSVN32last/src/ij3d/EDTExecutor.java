package ij3d;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

public class EDTExecutor {
    /**
     * Submits a Callable task to the AWT Event Dispatch Thread (EDT) and
     * returns a Future to wait for the result.
     */
    public static <T> Future<T> submit(final Callable<T> task) {
        final FutureTask<T> futureTask = new FutureTask<T>(task);
        // Queue the task to run on the EDT
        SwingUtilities.invokeLater(futureTask);
        return futureTask;
    }
}