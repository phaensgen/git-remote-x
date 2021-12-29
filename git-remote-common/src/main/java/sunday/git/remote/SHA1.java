package sunday.git.remote;

import java.util.Objects;

/**
 * Represents a SHA1 hash code value, as used by Git for object hashes.
 * 
 * @author Peter H&auml;nsgen
 */
public class SHA1
{
    public static final SHA1 EMPTY_TREE_HASH = new SHA1("4b825dc642cb6eb9a060e54bf8d69288fbee4904");

    private String sha1;

    /**
     * The constructor.
     */
    public SHA1(String sha1)
    {
        if (sha1.length() != 40)
        {
            throw new IllegalArgumentException("SHA1 must have a length of 40 characters.");
        }

        this.sha1 = sha1;
    }

    @Override
    public boolean equals(Object object)
    {
        if (object == null)
        {
            return false;
        }

        if (getClass() != object.getClass())
        {
            return false;
        }

        SHA1 that = (SHA1) object;
        return Objects.equals(sha1, that.sha1);
    }

    @Override
    public int hashCode()
    {
        return sha1.hashCode();
    }

    @Override
    public String toString()
    {
        return sha1;
    }
}
