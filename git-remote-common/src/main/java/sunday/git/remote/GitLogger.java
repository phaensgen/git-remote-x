package sunday.git.remote;

/**
 * A simple logger that writes messages to stderr according to git verbosity level.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitLogger
{
    private int verbosity;
    private String lastProgressMessage;

    public void setVerbosity(int verbosity)
    {
        this.verbosity = verbosity;
    }

    public int getVerbosity()
    {
        return verbosity;
    }

    public boolean isQuiet()
    {
        return verbosity == 0;
    }

    public boolean isDebug()
    {
        return verbosity > 1;
    }

    /**
     * Writes an error message no matter what verbosity is set.
     */
    public void error(String message)
    {
        clearProgress();
        System.err.println(message);
    }

    /**
     * Writes a message if the default verbosity is set.
     */
    public void info(String message)
    {
        if (verbosity >= 1)
        {
            clearProgress();
            System.err.println(message);
        }
    }

    /**
     * Writes a message if higher verbosity is set with -v.
     */
    public void debug(String message)
    {
        if (verbosity > 1)
        {
            clearProgress();
            System.err.println(message);
        }
    }

    /**
     * Writes a progress message, e.g. moves the cursor back before writing the next progress so that it will be shown
     * at the same position on the console.
     */
    public void progress(String message)
    {
        if (lastProgressMessage != null)
        {
            if (lastProgressMessage.length() < message.length())
            {
                // cursor back
                System.err.print("\r");

                // clear last message (which is longer) by printing spaces
                for (int i = 0; i < lastProgressMessage.length(); i++)
                {
                    System.err.print(" ");
                }
            }

            // cursor back for progress on same line
            System.err.print("\r");
        }

        System.err.print(message);
        lastProgressMessage = message;
    }

    private void clearProgress()
    {
        if (lastProgressMessage != null)
        {
            lastProgressMessage = null;
            System.err.println();
        }
    }
}
