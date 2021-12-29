package sunday.git.remote;

/**
 * Represents a regular git SHA1 reference in the form:
 * 
 * <pre>
 * {ref_sha1_value} {ref_name} [optional_attributes]
 * </pre>
 * 
 * for example:
 * 
 * <pre>
 * 0d4c51adb1af62cf63e2a7ef80dbad1bdf8c502e refs/heads/master
 * </pre>
 * 
 * @author Peter H&auml;nsgen
 */
public class SHA1Reference
{
    private SHA1 sha1;
    private String ref;

    /**
     * The constructor.
     */
    public SHA1Reference(SHA1 sha1, String ref)
    {
        this.sha1 = sha1;
        this.ref = ref;
    }

    public SHA1 getSha1()
    {
        return sha1;
    }

    public String getRef()
    {
        return ref;
    }

    public String toGit()
    {
        return sha1 + " " + ref;
    }
}
