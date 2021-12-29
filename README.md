# Git Remote X
This project provides a number of remote helpers for Git which support pushing data to various non-git remote repositories.
Git projects can be pushed to / pulled from the remote repository, just like to / from any other regular Git server.

The remote repository to be used can be configured like:

```
git remote add <remoteName> <url>
```

## git-remote-local
This helper uses a directory in the local file system as remote repository. It uses "local://" for the URL protocol.
The url specifies the path in the local file system where the objects will be stored.

Installation:

```
git-remote-local/install.sh
```

Configuration:

```
git remote add origin local:///<baseDir>/<repoName.git>
```


## git-remote-s3
This helper stores the remote files in an AWS S3 bucket. It uses "s3://" for the URL protocol.
The url specifies the S3 bucket name and the path in the bucket where the objects will be stored.
The AWS credentials to access the bucket must be configured in the local git configuration for the repository.

Example:

```
git config git.remote.s3enc.awsclientid <awsClientId>
git config git.remote.s3enc.awssecretkey <awsSecretKey>
git remote add origin s3://<bucketName>/<pathInBucket>/<repoName.git>
```

## git-remote-s3enc
This helper stores files in an AWS S3 bucket in an encrypted form. It uses "s3enc://" for the URL protocol.
The url specifies the S3 bucket name and the path in the bucket where the objects will be stored.
Anything that gets uploaded will be encrypted on the client-side using AES256. This affects path names as well as the actual file contents.
The encryption key and the AWS credentials to access the bucket must be configured in the local git configuration for the repository.

Example:

```
git config git.remote.s3enc.awsclientid <awsClientId>
git config git.remote.s3enc.awssecretkey <awsSecretKey>
git config git.remote.s3enc.encryptionkey <encryptionKey>
git remote add origin s3enc://<bucketName>/<path>/<repoName.git>
```

# Open Issues
* replace runtimeexceptions
* check process streaming
** use apache commons exec
* add git-remote-s3
** support large files
* add git-remote-s3enc
** support large files
* describe installation (link)
