# Google Guice integration

The `ratpack-guice` extension provides integration with [Google Guice](https://code.google.com/p/google-guice/).
The primary feature of this extension is to allow the server registry to be built by Guice.
That is, a Guice [`Injector`](http://google.github.io/guice/api-docs/4.0/javadoc/?com/google/inject/Injector.html) can be presented as a Ratpack [Registry](api/?ratpack/registry/Registry.html).
This allows the wiring of the application to be specified by Guice modules and bindings, but still allowing the registry to be the common integration layer between different Ratpack extensions at runtime.

The `ratpack-guice` module as of @ratpack-version@ is built against (and depends on) Guice @versions-guice@ (and [the multibindings extension](https://github.com/google/guice/wiki/Multibindings)).

## Modules

Guice provides the concept of a module, which is a kind of recipe for providing objects.
See Guice's [“Getting Started”](https://code.google.com/p/google-guice/wiki/GettingStarted) documentation for details.

The `ratpack-guice` library provides the [`BindingsSpec`](api/ratpack/guice/BindingsSpec.html) type for specifying the bindings for the application.

## Dependency injected handlers

The Guice integration gives you a means of decoupling the components of your application.
You can factor out functionality into standalone (i.e. non `Handler`) objects and use these objects from your handlers.
This makes your code more maintainable and more testable.
This is the standard “Dependency Injection” or “Inversion of Control” pattern.

The [Guice](api/ratpack/guice/Guice.html) class provides static `handler()` factory methods for creating root handlers that are the basis of the application.
These methods (the commonly used ones) expose [`Chain`](api/ratpack/handling/Chain.html) instance that can be used to build the application's handler chain.
The instance exposed by these methods provide a registry (via the [`getRegistry()`](api/ratpack/handling/Chain.html#getRegistry--)) method that can be used
to construct dependency injected handler instances.

See the documentation of the [Guice](api/ratpack/guice/Guice.html) class for example code.

## Modules as plugins

Ratpack does not have a formal plugin system. However, reusable functionality can be packaged as Guice modules.

For example, that `ratpack-jackson` library provides the [`JacksonModule`](api/ratpack/jackson/JacksonModule.html) class which is a Guice module.
To integrate Jackson into your Guice backed Ratpack application (e.g. for serializing objects as JSON), you simply use this module.

In Groovy script application this is as easy as:

```language-groovy groovy-ratpack
import ratpack.jackson.JacksonModule
import static ratpack.jackson.Jackson.json
import static ratpack.groovy.Groovy.ratpack

ratpack {
  bindings {
    add new JacksonModule()
  }
  handlers {
    get("some-json") {
      render json(user: 1)  // will render '{user: 1}'
    }
  }
}
```

See the [Guice package API documentation](api/ratpack/guice/package-summary.html) for more info on registering modules.

See the [Jackson package API documentation](api/ratpack/jackson/package-summary.html) for more info on using the Jackson integration.

## Guice and the context registry

TODO guice backed registry impl

This offers an alternative to dependency injected handlers, as objects can just be retrieved on demand from the context.

More usefully, this means that Ratpack infrastructure can be integrated via Guice modules.
For example, an implementation of the [`ServerErrorHandler`](api/ratpack/error/ServerErrorHandler.html) can be provided by a Guice module.
Because Guice bound objects are integrated into the context registry lookup mechanism, this implementation will participate in the error handling infrastructure.

This is true of all Ratpack infrastructure that works via context registry lookup, such as [Renderer](api/ratpack/render/Renderer.html) implementations for example.
