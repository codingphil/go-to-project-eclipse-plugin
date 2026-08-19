package dev.lonsing.eclipse.plugins.gotoproject.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.junit.jupiter.api.Test;

class ProjectComparatorTest {
  @Test
  void exactNameBreaksCaseInsensitiveTie() {
    IProject lowercase = project("alpha");
    IProject uppercase = project("Alpha");
    List<IProject> projects = new ArrayList<>(List.of(lowercase, uppercase));

    projects.sort(new ProjectComparator());

    assertEquals(List.of(uppercase, lowercase), projects);
  }

  private static IProject project(String name) {
    return (IProject) Proxy.newProxyInstance(IProject.class.getClassLoader(), new Class<?>[] { IProject.class },
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getName" -> name;
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          default -> null;
        });
  }
}
