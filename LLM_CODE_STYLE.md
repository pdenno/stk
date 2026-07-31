# LLM Code Style Preferences

## Clojure Style Guidelines

### Conditionals
- Only use `cond` for multiple condition branches
- Prefer `if-let` and `when-let` for binding and testing a value in one step
- Consider `when` for conditionals with single result and no else branch
- Consider `cond->`, and `cond->>`

### Variable Binding
- Minimize code points by avoiding unnecessary `let` bindings
- Only use `let` when a value is used multiple times or when clarity demands it
- Inline values used only once rather than binding them to variables
- Use threading macros (`->`, `->>`) to eliminate intermediate bindings

### Parameters & Destructuring
- Use destructuring in function parameters when accessing multiple keys
- Example: `[{:some-ns/keys [zloc match-form] :as ctx}]` for namespaced keys instead of separate `let` bindings
- Example: `[{:keys [zloc match-form] :as ctx}]` for regular keywords

### Control Flow
- Use early returns with `when` rather than deeply nested conditionals
- Return `nil` for "not found" conditions rather than objects with boolean flags

### Comments
- Don't add comments where the code is obvious. Prefer compact code.
- Don't add a blank line before a comment.
- Use comments only for complex algorithms, non-obvious business logic, or subtle gotchas
- When asked to add comments, focus on clarifying intent rather than narrating code

### Nesting
- Minimize nesting levels by using proper control flow constructs
- Use threading macros (`->`, `->>`, `as->`) for sequential operations

### Function Design
- Functions should generally do one thing
- Pure functions preferred over functions with side effects
- Return useful values that can be used by callers
- smaller functions make edits faster and reduce the number of tokens
- reducing tokens makes me happy

### Library Preferences
- Use `src/sched6/json.clj` for JSON read and write
- Use `malli` for schema confomance
- Prefer `clojure.string` functions over Java interop for string operations
  - Use `str/ends-with?` instead of `.endsWith`
  - Use `str/starts-with?` instead of `.startsWith`
  - Use `str/includes?` instead of `.contains`
  - Use `str/blank?` instead of checking `.isEmpty` or `.trim`
- Follow Clojure naming conventions: predicates end with `?`, functions that change state end with `!`
- Favor built-in Clojure functions that are more expressive and idiomatic

### Inter-line Spacing
- Don't eliminate extra spaces between tokens within a line; they are there for visual alignment.
```clojure
   'mount      'mount.core    ; 6 spaces here
   'p          'promesa.core  ; 10 spaces here so 'mount' start in same column as 'promesa'
```

### REPL best pratices
- Always reload namespaces with `:reload` flag: `(require '[namespace] :reload)`
- Use namespace aliases found in `develop.repl/alias-map`; add to that map as new files are created

### Testing Best Practices
- Always reload namespaces before running tests with `:reload` flag: `(require '[namespace] :reload)`
- Test both normal execution paths and error conditions
- Use `(clojure-test/run-tests <namespace>)` for focused testing; It is much faster than `clojure -X:test`.

### Using Shell Commands
- Prefer the idiomatic `clojure.java.shell/sh` for executing shell commands
- Always handle potential errors from shell command execution
- Use explicit working directory for relative paths: `(shell/sh "cmd" :dir "/path")`
- For testing builds, run `bin/kaocha` instead of running tests piecemeal
- When capturing shell output, remember it may be truncated for very large outputs
- Consider using shell commands for tasks that have mature CLI tools like diffing or git operations

### Clojure defn have the comment before the arguments
- The comment comes before the arguments:
  ```clojure
	  (defn my-fn
	  "comment"
	   [args]
	  ...)

	  ;; NOT
	  (defn bad-fn [args]
		"comment"
		...) ; It is not like common-lisp!
	  ```
- **Use :diag or :admin metadata** on function definitions that are only used by developers and in the REPL.
  ```clojure
	(defn ^:diag run-me-in-repl
	[]
	"Hi, Peter! No ordinary system code calls me.")
	```
