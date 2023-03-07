/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.file.impl.FileResolverImpl;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.Utils;
import io.vertx.core.impl.VertxInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class FileResolverTestBase extends VertxTestBase {

  private final String cacheBaseDir;

  protected FileResolverImpl resolver;

  private ClassLoader testCL;

  public FileResolverTestBase() {
    try {
      cacheBaseDir = new File(System.getProperty("java.io.tmpdir", ".") + File.separator + "vertx-cache").getCanonicalPath();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot resolve the canonical path to the cache dir", e);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    testCL = Thread.currentThread().getContextClassLoader();
    File baseDir = new File(new File(new File(new File("src"), "test"), "classpath"), "fileresolver");
    assertTrue(baseDir.exists() && baseDir.isDirectory());
    ClassLoader resourcesLoader = resourcesLoader(baseDir);
    Thread.currentThread().setContextClassLoader(resourcesLoader);
    resolver = new FileResolverImpl();
  }

  protected ClassLoader resourcesLoader(File baseDir) throws Exception {
    return Thread.currentThread().getContextClassLoader();
  }

  @Override
  protected void tearDown() throws Exception {
    Thread.currentThread().setContextClassLoader(testCL);
    resolver.close();
    super.tearDown();
  }

  @Test
  public void testReadFileInDirThenReadDir() {
    Buffer buff = vertx.fileSystem().readFileBlocking("webroot/subdir/subfile.html");
    assertEquals(buff.toString(), "<html><body>subfile</body></html>");
    Set<String> names = vertx.fileSystem().readDirBlocking("webroot/subdir").stream().map(path -> {
      int idx = path.lastIndexOf(File.separator);
      return idx == -1 ? path : path.substring(idx + 1);
    }).collect(Collectors.toSet());
    assertEquals(names, new HashSet<>(Arrays.asList("subdir2", "subfile.html")));
  }

  @Test
  public void testResolveNotExistFile() {
    File file = resolver.resolveFile("doesnotexist.txt");
    assertFalse(file.exists());
    assertEquals("doesnotexist.txt", file.getPath());
  }

  @Test
  public void testResolveNotExistDirectory() {
    File file = resolver.resolveFile("somedir");
    assertFalse(file.exists());
    assertEquals("somedir", file.getPath());
  }

  @Test
  public void testResolveNotExistFileInDirectory() {
    File file = resolver.resolveFile("somedir/doesnotexist.txt");
    assertFalse(file.exists());
    assertEquals("somedir" + File.separator + "doesnotexist.txt", file.getPath());
  }

  @Test
  public void testResolveFileFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>afile</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveFileFromClasspathDisableCaching() throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setFileSystemOptions(new FileSystemOptions().setFileCachingEnabled(true)));
    try {
      for (int i = 0; i < 2; i++) {
        File file = vertx.resolveFile("afile.html");
        assertTrue(file.exists());
        assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
        assertFalse(file.isDirectory());
        assertEquals("<html><body>afile</body></html>", readFile(file));
      }
    } finally {
      vertx.close();
    }
  }

  @Test
  public void testResolveFileWithSpacesFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afile with spaces.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>afile with spaces</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveFileWithSpaceAtEndFromClasspath() {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("afilewithspaceatend ");
      if (Utils.isWindows()) {
        assertFalse(file.exists());
      } else {
        assertTrue(file.exists());
        assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
        assertFalse(file.isDirectory());
        assertEquals("afilewithspaceatend ", readFile(file));
      }
    }
  }

  @Test
  public void testCacheDirIsPosix0700() throws Exception {
    Assume.assumeFalse(Utils.isWindows());

    File file = resolver.resolveFile("afile with spaces.html");
    assertTrue(file.exists());
    assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
    File parent = file.getParentFile(); // this should be the cache directory
    assertEquals(
      "rwx------",
      PosixFilePermissions.toString(Files.getPosixFilePermissions(parent.toPath())));
  }

  @Test
  public void testResolveDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("webroot");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertTrue(file.isDirectory());
    }
  }

  @Test
  public void testResolveFileInDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("webroot/somefile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>blah</body></html>", readFile(file));
    }
  }

  @Test
  public void testResolveSubDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("webroot/subdir");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertTrue(file.isDirectory());
    }
  }

  @Test
  public void testResolveFileInSubDirectoryFromClasspath() throws Exception {
    for (int i = 0; i < 2; i++) {
      File file = resolver.resolveFile("webroot/subdir/subfile.html");
      assertTrue(file.exists());
      assertTrue(file.getPath().startsWith(cacheBaseDir + "-"));
      assertFalse(file.isDirectory());
      assertEquals("<html><body>subfile</body></html>", readFile(file));
    }
  }

  @Test
  public void testRecursivelyUnpack() throws Exception {
    File file = resolver.resolveFile("webroot/subdir");
    assertTrue(file.exists());
    File sub = new File(file, "subfile.html");
    assertTrue(sub.exists());
    assertEquals("<html><body>subfile</body></html>", readFile(sub));
  }

  @Test
  public void testRecursivelyUnpack2() throws Exception {
    File file = resolver.resolveFile("webroot/subdir");
    assertTrue(file.exists());
    File sub = new File(new File(file, "subdir2"), "subfile2.html");
    assertTrue(sub.exists());
    assertEquals("<html><body>subfile2</body></html>", readFile(sub));
  }

  @Test
  public void testDeleteCacheDir() throws Exception {
    FileResolver resolver2 = new FileResolverImpl();
    File file = resolver2.resolveFile("webroot/somefile.html");
    assertTrue(file.exists());
    File cacheDir = file.getParentFile().getParentFile();
    assertTrue(cacheDir.exists());
    resolver2.close();
    assertFalse(cacheDir.exists());
  }

  @Test
  public void testCacheDirDeletedOnVertxClose() {
    VertxInternal vertx2 = (VertxInternal) vertx();
    File file = vertx2.resolveFile("webroot/somefile.html");
    assertTrue(file.exists());
    File cacheDir = file.getParentFile().getParentFile();
    assertTrue(cacheDir.exists());
    vertx2.close(onSuccess(v -> {
      assertFalse(cacheDir.exists());
      testComplete();
    }));
    await();
  }

  @Test
  public void testFileSystemReadFile() {
    assertTrue(vertx.fileSystem().existsBlocking("afile.html"));
    assertFalse(vertx.fileSystem().propsBlocking("afile.html").isDirectory());
    Buffer buffer = vertx.fileSystem().readFileBlocking("afile.html");
    assertNotNull(buffer);
    assertTrue(buffer.toString().startsWith("<html><body>afile</body></html>"));
  }

  @Test
  public void testFileSystemReadDirectory() {
    assertTrue(vertx.fileSystem().existsBlocking("webroot"));
    assertTrue(vertx.fileSystem().propsBlocking("webroot").isDirectory());
  }

  @Test
  public void testSendFileFromClasspath() {
    waitFor(2);
    vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(res -> {
      res.response()
        .sendFile("webroot/somefile.html")
        .onComplete(onSuccess(v -> complete()));
    }).listen(onSuccess(res -> {
      vertx.createHttpClient(new HttpClientOptions())
        .request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(HttpClientRequest::send)
        .onComplete(onSuccess(resp -> {
          resp.bodyHandler(buff -> {
            assertTrue(buff.toString().startsWith("<html><body>blah</body></html>"));
            complete();
          });
        }));
    }));
    await();
  }

  @Test
  public void testResolveFileFromDifferentThreads() throws Exception {
    int size = 10 * 1024 * 1024;
    byte[] content = new byte[size];
    new Random().nextBytes(content);

    File out = new File("target/test-classes/temp");
    if (out.exists()) {
      Files.delete(out.toPath());
    }
    Files.write(out.toPath(), content, StandardOpenOption.CREATE_NEW);

    int count = 100;
    CountDownLatch latch = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    List<Exception> errors = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Runnable runnable = () -> {
        try {
          start.await();
          File file = resolver.resolveFile("temp");
          byte[] data = Files.readAllBytes(file.toPath());
          Assert.assertArrayEquals(content, data);
        } catch (Exception e) {
          errors.add(e);
        } finally {
          latch.countDown();
        }
      };

      new Thread(runnable).start();
    }

    start.countDown();
    latch.await();
    assertTrue(errors.isEmpty());
  }

  @Test
  public void testEnableCaching() throws Exception {
    testCaching(true);
  }

  @Test
  public void testDisableCaching() throws Exception {
    testCaching(false);
  }

  private void testCaching(boolean enabled) throws Exception {
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setFileSystemOptions(new FileSystemOptions().setFileCachingEnabled(enabled)));
    File tmp = File.createTempFile("vertx", ".bin");
    tmp.deleteOnExit();
    URL url = tmp.toURI().toURL();
    Files.write(tmp.toPath(), "foo".getBytes());
    ClassLoader old = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(new ClassLoader() {
        @Override
        public URL getResource(String name) {
          if ("foo".equals(name)) {
            return url;
          }
          return super.getResource(name);
        }
      });
      File f = vertx.resolveFile("foo");
      assertEquals("foo", new String(Files.readAllBytes(f.toPath())));
      Files.write(tmp.toPath(), "bar".getBytes());
      f = vertx.resolveFile("foo");
      if (enabled) {
        assertEquals("foo", new String(Files.readAllBytes(f.toPath())));
      } else {
        assertEquals("bar", new String(Files.readAllBytes(f.toPath())));
      }
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  private String readFile(File file) {
    return vertx.fileSystem().readFileBlocking(file.getAbsolutePath()).toString();
  }


  @Test
  public void testResolveAfterCloseThrowsISE() throws Exception {
    FileResolver resolver2 = new FileResolverImpl();
    File file = resolver2.resolveFile("webroot/somefile.html");
    assertTrue(file.exists());
    File cacheDir = file.getParentFile().getParentFile();
    assertTrue(cacheDir.exists());
    resolver2.close();
    assertFalse(cacheDir.exists());
    try {
      resolver2.resolveFile("webroot/somefile.html");
      fail("Should fail");
    } catch (IllegalStateException e) {
      // OK
    }
  }

  @Test
  public void testResolveRelativeFileInDirectoryFromClasspath() {
    File somefile = resolver.resolveFile("a/a.txt");
    assertTrue(somefile.exists());
    assertTrue(somefile.getPath().startsWith(cacheBaseDir + "-"));
    assertFalse(somefile.isDirectory());
    // resolve a relative file
    File someotherfile = new File(new File(somefile.getParentFile().getParentFile(), "b"), "b.txt");
    assertFalse(someotherfile.exists()); // is hasn't been extracted yet
    someotherfile = resolver.resolveFile(someotherfile.getAbsolutePath());
    assertTrue(someotherfile.exists()); // is should be rebased and extracted now
    assertTrue(someotherfile.getPath().startsWith(cacheBaseDir + "-"));
    assertFalse(someotherfile.isDirectory());
  }

  @Test
  public void testDoNotResolveAbsoluteFileInDirectoryFromClasspath() {
    File somefile = resolver.resolveFile("/a/a.txt");
    assertFalse(somefile.exists());
  }

  @Test
  public void testResolveCacheDir() {
    File somefile = resolver.resolveFile("");
    assertTrue(somefile.exists());
    assertTrue(somefile.getPath().startsWith(cacheBaseDir + "-"));
    assertTrue(somefile.isDirectory());
  }

  @Test
  public void testReadFileInDirThenReadDirMultipleLevels() {
    Buffer buff = vertx.fileSystem().readFileBlocking("tree/a/b/c.txt");
    assertNotNull(buff);
    Function<List<String>, Set<String>> fx = l -> l.stream().map(path -> {
      int idx = path.lastIndexOf(File.separator);
      return idx == -1 ? path : path.substring(idx + 1);
    }).collect(Collectors.toSet());
    Set<String> names = fx.apply(vertx.fileSystem().readDirBlocking("tree/a/b"));
    assertEquals(new HashSet<>(Arrays.asList("c.txt")), names);
    names = fx.apply(vertx.fileSystem().readDirBlocking("tree/a"));
    assertEquals(new HashSet<>(Arrays.asList("b", "b.txt")), names);
  }

  @Test
  public void testReadFileInDirThenReadDirMultipleLevelsMissingResource() {
    Buffer buff = vertx.fileSystem().readFileBlocking("tree/a/b/c.txt");
    assertNotNull(buff);
    Function<List<String>, Set<String>> fx = l -> l.stream().map(path -> {
      int idx = path.lastIndexOf(File.separator);
      return idx == -1 ? path : path.substring(idx + 1);
    }).collect(Collectors.toSet());
    // "tree/a/d" doesn't exist
    try {
      vertx.fileSystem().readDirBlocking("tree/a/d");
      fail("should fail as it doesn't exist");
    } catch (RuntimeException e) {
      // OK
    }
    try {
      vertx.fileSystem().readDirBlocking("tree/a/d");
      fail("should fail as it doesn't exist");
    } catch (RuntimeException e) {
      // OK
    }
  }

  @Test
  public void testGetTheCacheDirWithoutHacks() {
    String cacheDir = resolver.cacheDir();
    if (cacheDir != null) {
      assertTrue(cacheDir.startsWith(cacheBaseDir + "-"));
      // strip the remaining
      String uuid = cacheDir.substring(cacheBaseDir.length() + 1);
      try {
        UUID.fromString(uuid);
        // OK
      } catch (Exception e) {
        fail("Expected a UUID");
      }
    }
  }
}
