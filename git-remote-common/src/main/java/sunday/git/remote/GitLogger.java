package sunday.git.remote;

/**
 * A simple logger that writes messages to stderr according to git verbosity level.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitLogger
{
    private int verbosity;

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

    /**
     * Writes a message unless its disabled.
     */
    public void print(String message)
    {
        if (!isQuiet())
        {
            System.err.print(message);
        }
    }

    /**
     * Writes a message unless its disabled.
     */
    public void println(String message)
    {
        if (!isQuiet())
        {
            System.err.println(message);
        }
    }

    /**
     * Writes an error message no matter what verbosity is set.
     */
    public void error(String message)
    {
        System.err.println(message);
    }

    /**
     * Writes a message if the default verbosity is set.
     */
    public void info(String message)
    {
        if (verbosity == 1)
        {
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
            System.err.println(message);
        }
    }
}
