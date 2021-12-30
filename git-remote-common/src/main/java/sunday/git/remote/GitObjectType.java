package sunday.git.remote;

/**
 * Enum representing the various git object types.
 * 
 * @author Peter H&auml;nsgen
 */
public enum GitObjectType
{
    COMMIT, BLOB, TAG, TREE;

    public String toLowerName()
    {
        return name().toLowerCase();
    }
}
