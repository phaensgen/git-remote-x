package sunday.git.remote;

/**
 * A general exception for all kinds of errors.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitRemoteException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * The constructor for a message.
     */
    public GitRemoteException(String message)
    {
        super(message);
    }

    /**
     * The constructor for a cause.
     */
    public GitRemoteException(Throwable cause)
    {
        super(cause);
    }

    /**
     * The constructor for a message and a cause.
     */
    public GitRemoteException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
