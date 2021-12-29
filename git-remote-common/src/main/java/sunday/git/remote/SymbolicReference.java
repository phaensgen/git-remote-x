package sunday.git.remote;

/**
 * Represents a symbolic reference in git, like used for HEAD. It looks like:
 * 
 * <pre>
 * &#64;{sym_ref_value} {sym_ref_name}
 * </pre>
 * 
 * for example:
 * 
 * <pre>
 * &#64;refs/heads/master HEAD
 * </pre>
 * 
 * @author Peter H&auml;nsgen
 */
public class SymbolicReference
{
    private String name;

    private String value;

    /**
     * The constructor.
     */
    public SymbolicReference(String name, String value)
    {
        this.name = name;
        this.value = value;
    }

    public String getName()
    {
        return name;
    }

    public String getValue()
    {
        return value;
    }

    public String toGit()
    {
        return "@" + value + " " + name;
    }
}
