package com.hatkid.mkxpz;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class GameFilesTest
{
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File game(String name) throws Exception {
        File f = temp.newFolder(name);
        assertTrue(new File(f, "Game.ini").createNewFile());
        return f;
    }

    private File validate(File f, List<File> protectedFolders, List<File> others, boolean requireGame) throws Exception {
        return GameFiles.validate(f, Collections.singletonList(temp.getRoot()), protectedFolders, others, requireGame);
    }

    private void rejected(File f, List<File> protectedFolders, List<File> others, boolean requireGame) throws Exception {
        try { validate(f, protectedFolders, others, requireGame); fail("Unsafe target accepted"); }
        catch (IOException expected) { }
    }

    @Test public void deletesOnlyTheSelectedGameIncludingHiddenFilesAndSaves() throws Exception {
        File selected = game("Selected"), other = game("Other");
        File data = new File(selected, "Data"); assertTrue(data.mkdir());
        Files.write(new File(data, "Scripts.rxdata").toPath(), new byte[] {1,2,3});
        Files.write(new File(selected, "Game.rxdata").toPath(), new byte[] {4});
        assertTrue(new File(selected, ".hidden").createNewFile());
        File target = validate(selected, Collections.emptyList(), Collections.singletonList(other), true);
        GameFiles.delete(target);
        assertFalse(selected.exists());
        assertTrue(new File(other, "Game.ini").isFile());
    }

    @Test public void rejectsTheStorageRootEvenWhenItContainsAGameMarker() throws Exception {
        assertTrue(new File(temp.getRoot(), "Game.ini").createNewFile());
        rejected(temp.getRoot(), Collections.emptyList(), Collections.emptyList(), true);
        assertTrue(new File(temp.getRoot(), "Game.ini").exists());
    }

    @Test public void rejectsCommonFoldersAndParentsOfProtectedFolders() throws Exception {
        File common = game("Downloads"), child = new File(common, "Personal"); assertTrue(child.mkdir());
        rejected(common, Collections.singletonList(common), Collections.emptyList(), true);
        rejected(common, Collections.singletonList(child), Collections.emptyList(), true);
        assertTrue(new File(common, "Game.ini").exists());
    }

    @Test public void rejectsParentChildAndDuplicateGameRegistrations() throws Exception {
        File selected = game("Selected"), nested = new File(selected, "Nested"); assertTrue(nested.mkdir());
        rejected(selected, Collections.emptyList(), Collections.singletonList(nested), true);
        rejected(nested, Collections.emptyList(), Collections.singletonList(selected), false);
        rejected(selected, Collections.emptyList(), Collections.singletonList(selected), true);
    }

    @Test public void preflightRejectsAnUnregisteredNestedGameWithoutRemovingAnyFiles() throws Exception {
        File selected = game("Selected"), nested = new File(selected, "Nested"); assertTrue(nested.mkdir());
        assertTrue(new File(nested, "Game.exe").createNewFile());
        File keep = new File(selected, "keep.txt"); assertTrue(keep.createNewFile());
        rejected(selected, Collections.emptyList(), Collections.emptyList(), true);
        assertTrue(keep.exists());
        assertTrue(new File(nested, "Game.exe").exists());
    }

    @Test public void rejectsAnOrdinaryFolderAndAnythingOutsideTheAllowedVolume() throws Exception {
        File empty = temp.newFolder("Empty"), selected = game("Selected");
        rejected(empty, Collections.emptyList(), Collections.emptyList(), true);
        try {
            GameFiles.validate(selected, Collections.singletonList(empty), Collections.emptyList(), Collections.emptyList(), true);
            fail("Outside target accepted");
        } catch (IOException expected) { }
        assertTrue(selected.exists());
    }

    @Test public void symbolicLinksAreRemovedWithoutTouchingTheirTargets() throws Exception {
        File selected = game("Selected"), outside = temp.newFolder("Outside");
        File keep = new File(outside, "keep.txt"); assertTrue(keep.createNewFile());
        Files.createSymbolicLink(new File(selected, "ExternalFolder").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(selected, "ExternalFile").toPath(), keep.toPath());
        Files.createSymbolicLink(new File(selected, "BrokenLink").toPath(), new File(outside,"missing").toPath());
        GameFiles.delete(validate(selected, Collections.emptyList(), Collections.emptyList(), true));
        assertFalse(selected.exists());
        assertTrue(keep.exists());
    }

    @Test public void rejectsASymbolicLinkUsedAsTheGameRoot() throws Exception {
        File selected = game("Selected"), alias = new File(temp.getRoot(), "Alias");
        Files.createSymbolicLink(alias.toPath(), selected.toPath());
        rejected(alias, Collections.emptyList(), Collections.emptyList(), true);
        assertTrue(selected.exists());
    }

    @Test public void anOutsideLinkInsertedAfterPreflightStillCannotDeleteOutsideFiles() throws Exception {
        File selected = game("Selected"), outside = temp.newFolder("Outside");
        File keep = new File(outside, "keep.txt"); assertTrue(keep.createNewFile());
        File target = validate(selected, Collections.emptyList(), Collections.emptyList(), true);
        Files.createSymbolicLink(new File(selected, "LateLink").toPath(), outside.toPath());
        GameFiles.delete(target);
        assertTrue(keep.exists());
    }

    @Test public void aNewNestedGameAppearingAfterPreflightStopsDeletion() throws Exception {
        File selected = game("Selected");
        File target = validate(selected, Collections.emptyList(), Collections.emptyList(), true);
        File nested = new File(selected, "Nested"); assertTrue(nested.mkdir());
        assertTrue(new File(nested, "Game.ini").createNewFile());
        try { GameFiles.delete(target); fail("New nested game deleted"); }
        catch (IOException expected) { }
        assertTrue(new File(nested, "Game.ini").exists());
        assertTrue(new File(selected, "Game.ini").exists());
    }

    @Test public void permissionFailureIsReportedAndKeepsTheGameMarker() throws Exception {
        File selected = game("Selected"), data = new File(selected,"Data"); assertTrue(data.mkdir());
        File keep = new File(data,"keep.txt"); assertTrue(keep.createNewFile());
        File target = validate(selected, Collections.emptyList(), Collections.emptyList(), true);
        try {
            data.setWritable(false, false);
            assumeFalse("Filesystem cannot model denied deletion", data.canWrite());
            try { GameFiles.delete(target); fail("Deletion failure was ignored"); }
            catch (IOException expected) { }
            assertTrue(new File(selected, "Game.ini").exists());
            assertTrue(keep.exists());
        } finally { data.setWritable(true, false); }
    }

    @Test public void gameAndGameTwoAreSiblingsNotOverlappingPrefixes() throws Exception {
        File selected = game("Game"), other = game("GameTwo");
        assertFalse(GameFiles.contains(selected, other));
        GameFiles.delete(validate(selected, Collections.emptyList(), Arrays.asList(other), true));
        assertTrue(other.exists());
    }
}
