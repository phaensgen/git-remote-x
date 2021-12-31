[![Build Actions Status](https://github.com/phaensgen/git-remote-x/workflows/build/badge.svg)](https://github.com/phaensgen/git-remote-x/actions)

# Git Remote X
This project provides a number of remote helpers for Git which support pushing data to various non-git remote repositories.
Git projects can be pushed to / pulled from the remote repository, just like to / from any other regular Git server.

The remote repository to be used can be configured like:

```
git remote add <remoteName> <url>
```

### Building
This is a Maven Java project, which can be built like:

```
mvn install
```

Java 17 is required (not code-wise, but just because its the newest).
Note that during build some integration tests will be executed, which require git and the git-remote-x helpers on the path (see below).
So it's a good idea to first copy the executables to a location where they can be found during the build, even if they still point
to an invalid artifact.

## git-remote-local
This helper uses a directory in the local file system as remote repository. It uses "local://" for the URL protocol.
The url specifies the path in the local file system where the objects will be stored.

### Installation
Git needs an executable in the path with the name "git-remote-local" in order to handle the "local://" protocol.
You can copy the provided script:

```
cp git-remote-local/git-remote-local /usr/local/bin/
```

### Configuration
The remote repository for an existing git repo can be added like this:

```
git remote add origin local:///<baseDir>/<repoName>
```

You can use origin for the name, or give it any other name if you want to have multiple remotes.

Examples:

```
git remote add origin local:///mypath/myrepo.git
```

The repository can then be uploaded using git push (with the setting it as upstream at the first call):

```
git push --set-upstream origin master
```

After a repository has been pushed, it can be cloned to a different location like:

```
git clone local:///mypath/myrepo.git
```


## git-remote-s3
This helper stores the remote files in an AWS S3 bucket. It uses "s3://" for the URL protocol.
The URL specifies the S3 bucket name and and optional path in the bucket where the objects will be stored.
The AWS credentials to access the bucket must be configured in the global git configuration.

### Installation
Git needs an executable in the path with the name "git-remote-s3" in order to handle the "s3://" protocol.
You can copy the provided script:

```
cp git-remote-s3/git-remote-s3 /usr/local/bin/
```

### Configuration
In order to access the S3 bucket, the AWS credentials must be configured. For an existing repository, this could be
done in the local configuration, however since this would not work for the "git clone" command it is better to use the global configuration:

```
git config --global s3.accesskeyid <awsAccessKeyId>
git config --global s3.secretkey <awsSecretKey>
git config --global s3.region <awsRegion>
git remote add origin s3://<bucketName>/<pathInBucket>/<repoName>
```

Examples:

```
git config --global s3.accesskeyid ...
git config --global s3.secretkey ...
git config --global s3.region eu-central-1
git remote add origin s3://mybucket/myrepo.git
```


## git-remote-s3enc
This helper stores files in an AWS S3 bucket in an encrypted form. It uses "s3enc://" for the URL protocol.
The URL specifies the S3 bucket name and the optional path in the bucket where the objects will be stored.
Anything that gets uploaded will be encrypted on the client-side using AES256. It is not necessary to activate server-side
encryption for the bucket, as everything it contains is already encrypted.
Encryption affects only the actual file contents. Path names are mostly SHA1 hash codes in git anyway, so they don't represent anything secret
and must not be encrypted additionally.
The encryption key and the AWS credentials to access the bucket must be configured in the global git configuration for the repository.

### Installation
Git needs an executable in the path with the name "git-remote-s3enc" in order to handle the "s3enc://" protocol.
You can copy the provided script:

```
cp git-remote-s3enc/git-remote-s3enc /usr/local/bin/
```

### Configuration
The configuration is similar to the S3 configuration above, except that there is an additional setting for the
encryption key.

```
git config --global s3enc.accesskeyid <awsAccessKeyId>
git config --global s3enc.secretkey <awsSecretKey>
git config --global s3enc.region <awsRegion>
git config --global s3enc.encryptionkey <encryptionKey>
git remote add origin s3enc://<bucketName>/<pathInBucket>/<repoName>
```

The encryption key is a Base64 encoded AES key (which is simply an array of 32 random bytes). To make it easier to generate such a key,
the git-remote-s3enc tool can be called with the -generateKey option:

```
git-remote-s3enc -generateKey
```

This produces a key that can be configured at git for encryption, for example like this:

```
git config --global s3.encryptionkey $(git-remote-s3enc -generateKey)
```

The key should be backed up in a safe place, for example in a password manager, because you will need it do decrypt things again!
If you loose it, your bucket contents will be lost, too. 
If you want to clone the repository to another machine, you need to configure the same encryption key there first.

# Open Issues
* support large files for s3
* support large files for s3enc

# Disclaimer
This project is intended for educational purposes only. It should not be used for production use! Make sure you have a backup of your data
before using this tool!
