/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withMinSdkVersion;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withOnDemand;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withSplitId;
import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.withUsesSplit;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.XmlNode;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleDependencyValidatorTest {

  private static final String PKG_NAME = "com.test.app";

  @Test
  public void validateAllModules_validJustBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("base", androidManifest(PKG_NAME)));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_missingBase_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("not_base", androidManifest(PKG_NAME, withSplitId("not_base"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("Mandatory 'base' module is missing");
  }

  @Test
  public void validateAllModules_validTree_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("featureA", androidManifest(PKG_NAME)), // implicitly depends on base
            module("subFeatureA1", androidManifest(PKG_NAME, withUsesSplit("featureA"))),
            module("featureB", androidManifest(PKG_NAME)), // implicitly depends on base
            module("subFeatureB1", androidManifest(PKG_NAME, withUsesSplit("featureB"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_validDiamond_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("f1", androidManifest(PKG_NAME)), // implicitly depends on base
            module("f2", androidManifest(PKG_NAME)), // implicitly depends on base
            module("f12", androidManifest(PKG_NAME, withUsesSplit("f1", "f2"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_reflexiveDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature", androidManifest(PKG_NAME, withUsesSplit("feature"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("depends on itself");
  }

  @Test
  public void validateAllModules_duplicateDependencies_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature", androidManifest(PKG_NAME)), // implicitly depends on base
            module("sub_feature", androidManifest(PKG_NAME, withUsesSplit("feature", "feature"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("declares dependency on module 'feature' multiple times");
  }

  @Test
  public void validateAllModules_referencesUnknownModule_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("featureA", androidManifest(PKG_NAME, withUsesSplit("unknown"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("Module 'unknown' is referenced by <uses-split> but does not exist");
  }

  @Test
  public void validateAllModules_cycle_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("module1", androidManifest(PKG_NAME, withUsesSplit("module2"))),
            module("module2", androidManifest(PKG_NAME, withUsesSplit("module3"))),
            module("module3", androidManifest(PKG_NAME, withUsesSplit("module1"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("Found cyclic dependency between modules");
  }

  @Test
  public void validateAllModules_baseDeclaresSplitId_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(module("base", androidManifest(PKG_NAME, withSplitId("base"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception).hasMessageThat().contains("should not declare split ID");
  }

  @Test
  public void validateAllModules_splitIdDifferentFromModuleName_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature", androidManifest(PKG_NAME, withSplitId("not_feature"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains("Module 'feature' declares in its manifest that the split ID is 'not_feature'");
  }

  private BundleModule module(String moduleName, XmlNode manifest) throws IOException {
    return new BundleModuleBuilder(moduleName).setManifest(manifest).build();
  }

  @Test
  public void validateAllModules_installTimeToOnDemandModulesDependency_throws() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME, withOnDemand(true))),
            module("feature2", androidManifest(PKG_NAME, withUsesSplit("feature1"))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature2' declares dependency on on-demand module 'feature1'");
  }

  @Test
  public void validateAllModules_installTimeToInstallTimeModulesDependency_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME)),
            module("feature2", androidManifest(PKG_NAME, withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandToInstallTimeModulesDependency_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME)),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemand(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandToOnDemandModulesDependency_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME)),
            module("feature1", androidManifest(PKG_NAME, withOnDemand(true))),
            module(
                "feature2",
                androidManifest(PKG_NAME, withOnDemand(true), withUsesSplit("feature1"))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkSmallerThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1", androidManifest(PKG_NAME, withOnDemand(true), withMinSdkVersion(19))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleEffectiveMinSdkSmallerThanBase_succeeds()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withOnDemand(true))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkSmallerThanOnDemandModule_fails()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(
                    PKG_NAME,
                    withOnDemand(true),
                    withUsesSplit("feature2"),
                    withMinSdkVersion(19))),
            module(
                "feature2", androidManifest(PKG_NAME, withOnDemand(true), withMinSdkVersion(20))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "On-demand module 'feature1' has a minSdkVersion(19), which is smaller than the"
                + " minSdkVersion(20) of its dependency 'feature2'.");
  }

  @Test
  public void validateAllModules_onDemandModuleEffectiveMinSdkSmallerThanOnDemandModule_fails()
      throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1",
                androidManifest(PKG_NAME, withOnDemand(true), withUsesSplit("feature2"))),
            module(
                "feature2", androidManifest(PKG_NAME, withOnDemand(true), withMinSdkVersion(20))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "On-demand module 'feature1' has a minSdkVersion(1), which is smaller than the"
                + " minSdkVersion(20) of its dependency 'feature2'.");
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkGreaterThanBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1", androidManifest(PKG_NAME, withOnDemand(true), withMinSdkVersion(21))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_onDemandModuleMinSdkEqualToBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module(
                "feature1", androidManifest(PKG_NAME, withOnDemand(true), withMinSdkVersion(20))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkGreaterThanBase_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(21))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(21) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkSmallerThanBase_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(19))));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(19) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }

  @Test
  public void validateAllModules_installTimeModuleMinSdkEqualToBase_succeeds() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME, withMinSdkVersion(20))));

    new ModuleDependencyValidator().validateAllModules(allModules);
  }

  @Test
  public void validateAllModules_installTimeModuleEffectiveMinSdk_fails() throws Exception {
    ImmutableList<BundleModule> allModules =
        ImmutableList.of(
            module("base", androidManifest(PKG_NAME, withMinSdkVersion(20))),
            module("feature1", androidManifest(PKG_NAME)));

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> new ModuleDependencyValidator().validateAllModules(allModules));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Install-time module 'feature1' has a minSdkVersion(1) different than the"
                + " minSdkVersion(20) of its dependency 'base'.");
  }
}
