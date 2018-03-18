# Diff Dispatcher
A simple annotation processor for generating data class changes dispatcher

Applications which use unidirectional UI archictures have UI state passed to a single "render"-method. Sometimes it is needed to render only parts of the state which are actually changed.

Diff Dispatcher can help by generating boilerplate code which does all the necessary checks.

# Example

To enable generation of change dispatcher, annotate a data class represeting your view state with `@DiffElement` annotation:

```kotlin
@DiffElement(diffReceiver = ViewStateRenderer::class)
data class ViewState(
    val users: List<User>,
    val categories: Map<String, Category>,
    val showProgressBar: Boolean,
    val showError: ErrorDescription?
)
```

Next, you will need to provide a _diff receiver_ interface which will be used to generate an actual dispatcher implementation with necessary checks:

```kotlin
interface ViewStateRenderer {
    fun renderUsers(users: List<User>)
    fun renderCategories(categories: Map<String, Category>, users: List<User>)
    fun renderLoadingErrorState(showProgressBar: Boolean, showError: ErrorDescription?)
}
```
There can be any number of methods in the receiver-interface and they can have arbitrary names, but their argument names must match the names of the `ViewState` fields.

Note how any field can be used in different methods  — this will simply cause the necessary check to be generated, so that method will be called only if all fields mentioned in its arguments have changed.

The `ViewStateRenderer` interface above will cause a _dispatcher_ class to be auto-generated which will look roughly like this:

```kotlin
class ViewStateRendererDispatcher(private val receiver: ViewStateRenderer) {
   fun dispatch(viewState: ViewState, previousViewState: ViewState?) {
      if (viewState.users != previousViewState?.users) {
          receiver.renderUsers(viewState.users)
      }
      if (viewState.categories != previousViewState?.categories 
            || viewState.users != previuosViewState?.users) {
          receiver.renderCategories(viewState.categories, viewState.users)
      }
      if (viewState.showProgressBar != previousViewState?.showProgressBar 
             || viewState.showError != previuosViewState?.showError) {
          receiver.renderLoadingErrorState(viewState.showProgressBar, viewState.showError)
      }
   }
}
```
(This is just to show which boilerplate will be generated)

All you have to do after you add annotation and receiver-interface is to use a generated builder to create this dispatcher for you:

```kotlin
class MyFragmentOrActivity : BaseActivityOrFragment, ViewStateRenderer {
    private val renderDispatcher = ViewStateDiffDispatcher.Builder()
        .target(this) // <-- a class wich implements ViewStateRenderer and will receive render calls
        .build()
    private var previousViewState: ViewState? = null
        
    fun render(viewState: ViewState) {
        renderDispatcher.dispatch(viewState, previousViewState)
        previousViewState = viewState;
    }
    
    override fun renderUsers(users: List<User>) {
       // render users
    }
    
    override fun renderCategories(categories: Map<String, Category>, users: List<User>) {
       // render categories
    }
    
    override fun renderLoadingErrorState(showProgressBar: Boolean, showError: ErrorDescription?) {
       // render loading/error state
    }
}
```

**NOTE:** This library was created with processing Kotlin's data classes in mind, but it should also work for Java classes. Although it's not as thoroughly tested yet. If something doesn't work, please create an issue.

# Download

Add a Gradle dependency:

```gradle
apply plugin: 'kotlin-kapt'

implementation 'com.github.dimsuz:diff-dispatcher-annotations:0.9.1'
kapt 'com.github.dimsuz:diff-dispatcher-processor:0.9.1'
```

# License

```
   Copyright 2018 Dmitry Suzdalev

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and 
   limitations under the License.
```
