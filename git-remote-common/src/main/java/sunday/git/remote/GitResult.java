package sunday.git.remote;

/**
 * Represents the result of a git command-line invocation.
 * 
 * @author Peter H&auml;nsgen
 */
public class GitResult
{
    private int exitValue;
    private byte[] output;

    /**
     * The constructor.
     */
    public GitResult(int exitValue, byte[] output)
    {
        this.exitValue = exitValue;
        this.output = output;
    }

    public int getExitValue()
    {
        return exitValue;
    }

    /**
     * Returns true if the exit value was 0.
     */
    public boolean isOK()
    {
        return exitValue == 0;
    }

    /**
     * Returns the result as binary.
     */
    public byte[] getOutput()
    {
        return output;
    }

    /**
     * Returns the first line of a string output.
     */
    public String getFirstLine()
    {
        return getLines()[0];
    }

    /**
     * Returns all lines of a string output.
     */
    public String[] getLines()
    {
        String s = new String(output);
        return s.split("\\n");
    }
}
