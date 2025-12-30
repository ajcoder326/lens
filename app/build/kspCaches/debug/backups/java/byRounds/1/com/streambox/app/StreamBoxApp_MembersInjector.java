package com.streambox.app;

import com.streambox.app.extension.ExtensionInstaller;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class StreamBoxApp_MembersInjector implements MembersInjector<StreamBoxApp> {
  private final Provider<ExtensionInstaller> extensionInstallerProvider;

  public StreamBoxApp_MembersInjector(Provider<ExtensionInstaller> extensionInstallerProvider) {
    this.extensionInstallerProvider = extensionInstallerProvider;
  }

  public static MembersInjector<StreamBoxApp> create(
      Provider<ExtensionInstaller> extensionInstallerProvider) {
    return new StreamBoxApp_MembersInjector(extensionInstallerProvider);
  }

  @Override
  public void injectMembers(StreamBoxApp instance) {
    injectExtensionInstaller(instance, extensionInstallerProvider.get());
  }

  @InjectedFieldSignature("com.streambox.app.StreamBoxApp.extensionInstaller")
  public static void injectExtensionInstaller(StreamBoxApp instance,
      ExtensionInstaller extensionInstaller) {
    instance.extensionInstaller = extensionInstaller;
  }
}
