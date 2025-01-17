
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitComInf extends Properties {

    final private String BRANCHPROP_LABEL = "branch";
    final private String REVPROP_LABEL = "releaseNr"; // git commit number
    final private String DATEPROP_LABEL = "DSTAMP"; // git commit date
    private String repoPath; // path to repository root directory

    public void setRepoPath(final String repoPath) {
        this.repoPath = repoPath;
    }

    public void execute() {

        String branch = null;
        String revision = null;
        String lastTag = null;
        String commitDate = null;

        Repository repo = null;
        Git git = null;
        RevWalk walk = null;
        try {
            final File src = new File(this.repoPath);
            repo = new FileRepositoryBuilder().readEnvironment()
                    .findGitDir(src).build();
            branch = repo.getBranch();
            branch = "master".equals(branch) ? "" : "_" + branch;
            this.setProperty(this.BRANCHPROP_LABEL, branch);
            final ObjectId head = repo.resolve("HEAD");

            git = new Git(repo);
            final List<Ref> tags = git.tagList().call();

            walk = new RevWalk(repo);
            final RevCommit headCommit = walk.parseCommit(head);
            final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
            commitDate = df.format(headCommit.getAuthorIdent().getWhen());
            walk.markStart(headCommit);
            int distance = 0;

            /* Peel known tags */
            final List<Ref> peeledTags = new ArrayList<>();
            for (final Ref tag : tags) {
                peeledTags.add(repo.peel(tag));
            }

            /* Look for the last tag commit and calculate distance with the HEAD commit */
            for (final RevCommit commit : walk) {
                for (final Ref tag : peeledTags) {
                    if (commit.equals(tag.getPeeledObjectId()) || commit.equals(tag.getObjectId())) {
                        lastTag = commit.getShortMessage();
                        break;
                    }
                }
                if (lastTag != null || distance++ > 90999) {
                    break;
                }
            }
            walk.dispose();
            if (lastTag == null) {
                revision = "0000";
            } else {
                revision = Integer.toString(distance + 9000);
            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (GitAPIException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            /* In all cases, properly release resources */
            if (walk != null) {
                walk.close();
            }
            if (git != null) {
                git.close();
            }
            if (repo != null) {
                repo.close();
            }
        }

        this.setProperty(this.BRANCHPROP_LABEL, branch);
//this.setProperty(branch, branch)
        this.setProperty(this.REVPROP_LABEL, revision);
        this.setProperty("REPL_REVISION_NR", revision);
        this.setProperty(this.DATEPROP_LABEL, commitDate);
        this.setProperty("REPL_DATE", commitDate);
    }

    /**
     * use: GitComInf.jar pathtoGitRepro outputfile gitbuildnumber.properties
     *
     * @param args = path to Git repository (default . )
     * @param args (optional) = filename for result (default
     * ./gitbuildnumber.properties)
     */
    public static void main(String[] args) {
        GitComInf gitRevTask = new GitComInf();
        if (args.length == 0) {
            gitRevTask.setRepoPath("."); // path to root of git repository
        } else {
            gitRevTask.setRepoPath(args[0]);
        }

        gitRevTask.execute();
        System.out.println("Git Commit Info: " + gitRevTask.toString());

        // output result to property file
        File f;
        if (args.length > 1) {
            f = new File(args[1]);
        } else {
            f = new File("gitbuildnumber.properties");
        }
        try {
            if (!f.exists()) { 
                f.getParentFile().mkdirs();
             //   Files.createDirectories(f.getParentFile().toPath()); // just make sure missing dir is not a problem
            }
            f.createNewFile();
            FileWriter w = new FileWriter(f);
            gitRevTask.store(w, "");
            w.close();
        } catch (final IOException ex) {
            System.out.println(ex.toString());
            System.out.println (args[1]);
        }
    }
}
