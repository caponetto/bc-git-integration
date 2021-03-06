/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package porcelli.me.git.integration.githook.command;

import java.io.IOException;
import java.util.Spliterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import static java.util.stream.StreamSupport.stream;

public class SquashCommand implements Command {

    private final String branch;
    private final Git git;
    private String squashedCommitMessage;
    private String startCommitString;

    public SquashCommand(final Git git,
                         final String branch,
                         final String startCommitString,
                         final String squashedCommitMessage) throws IOException {
        this.git = git;
        this.squashedCommitMessage = squashedCommitMessage;
        this.branch = branch;
        this.startCommitString = git.getRepository().resolve(startCommitString).name();
    }

    public void execute(final RevCommit latestCommit) throws IOException {
        final Repository repo = this.git.getRepository();

        final RevCommit startCommit = checkIfCommitIsPresentAtBranch(this.git,
                                                                     this.branch,
                                                                     this.startCommitString);

        RevCommit parent = startCommit;
        if (startCommit.getParentCount() > 0) {
            parent = startCommit.getParent(0);
        }

        final CommitBuilder commitBuilder = new CommitBuilder();
        commitBuilder.setParentId(parent);
        commitBuilder.setTreeId(latestCommit.getTree().getId());
        commitBuilder.setMessage(squashedCommitMessage);
        commitBuilder.setAuthor(startCommit.getAuthorIdent());
        commitBuilder.setCommitter(startCommit.getAuthorIdent());

        try (final ObjectInserter odi = repo.newObjectInserter()) {
            final RevCommit squashedCommit = git.getRepository().parseCommit(odi.insert(commitBuilder));
            new RefUpdateCommand(git, branch, latestCommit).execute(squashedCommit);
        } catch (ConcurrentRefUpdateException | IOException e) {
            throw new RuntimeException("Error on executing squash.", e);
        }
    }

    private RevCommit checkIfCommitIsPresentAtBranch(final Git git,
                                                     final String branch,
                                                     final String startCommitString) throws IOException {

        try {
            final ObjectId id = git.getRepository().exactRef("refs/heads/" + branch).getObjectId();
            final Spliterator<RevCommit> log = git.log().add(id).call().spliterator();
            return stream(log, false)
                    .filter((elem) -> elem.getName().equals(startCommitString))
                    .findFirst().orElseThrow(() -> new RuntimeException("Commit is not present at branch " + branch));
        } catch (GitAPIException | MissingObjectException | IncorrectObjectTypeException e) {
            throw new RuntimeException("A problem occurred when trying to get commit list", e);
        }
    }
}

