# OnlineEvoSpeak

OnlineEvoSpeak (formerly EvoSpeakV1) is the automatically invoked LLM warm-start implementation. [OfflineEvoSpeak](../OfflineEvoSpeak/README.md), formerly the EvoSpeak directory, retains the legacy offline training implementation and documents manually invoking an LLM with the same prompt. OnlineEvoSpeak retains dual-tree GP, tournament selection, crossover/mutation/reproduction and weighted scheduling objectives.

The migration updates source packages, params, `GPMain`, tests and VS Code launch/task paths. The main class is now `mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain`; rebuild the project and update any saved IDEA run configuration referring to the old package. Existing private params and run directories move with the folder unchanged. Historical status files and serialized checkpoints retain their original paths/class names and are not rewritten; use the new location when opening old artifacts. Cross-version checkpoint compatibility is not guaranteed. The `evospeak.*` parameter names and per-run output filenames are unchanged.

The workflow is:

1. Load the scheduling scenario, objective mode, weights and LLM settings from params.
2. Load the provided reference heuristics and ask the selected LLM to extract terminal-grounded insights, then generate a small batch of new pairs with explanations of their expected objective effects. Both objective modes use bullet insights and annotated ECJ-style text.
3. Check the response schema and reference IDs, parse against the actual ECJ GP grammar, enforce tree limits, reject duplicate/reference-copy pairs, and run bounded scheduling simulations on validation seeds.
4. Feed rejection evidence back to the LLM and request replacements within the configured budget.
5. Publish `generated-population.txt` in native ECJ format only when the required population is complete.
6. Start a fresh GP state and load that TXT through ECJ's `pop.subpop.0.file` mechanism, then evaluate and evolve the loaded individuals. This is the same workflow for single and weighted multi-objective runs.

Preparation sets up only the grammar and species prototypes needed for validation; it does not create random initial individuals or initialize GP statistics. The training state is created after the complete TXT has been saved. No generated Java/Python/shell code is executed. LLM text is treated as expression data. Failed generation, invalid credentials, malformed output or insufficient feasible individuals prevent GP from starting. There is no silent random-population fallback.

## Quick Start

Requirements: JDK 11+, the existing `libraries` JARs, and PowerShell 7 for the convenience script. Tested with OpenJDK 26.0.1; the script targets Java 11 bytecode. `JAVA_HOME`, a JDK on PATH, or a JDK under the user's `.jdks` directory is detected automatically.

In VS Code, select one of these Run and Debug configurations:

- **OnlineEvoSpeak: Offline Smoke**: no API key required; two handwritten example rules, three GP generations.
- **OnlineEvoSpeak: LLM Generate and Train**: uses `evospeak.params` and automatically calls the configured LLM.
- **OnlineEvoSpeak: Explain Latest Population**: analyzes the most recent generated population using the configured LLM.

The pre-launch task compiles the required source and reflection-loaded ECJ classes. In IntelliJ, run `EvoSpeakMain.main()` or select OnlineEvoSpeak in `GPMain.main()` with the repository root as the working directory and `libraries/*.jar` on the classpath. Running `GPRun` directly is not the V1 entry point because it bypasses generation configuration.

From the repository root:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\OnlineEvoSpeak\smoke.params
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action train
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action test
```

The first command checks the local environment without a model. The second needs the LLM configuration below. The regression tests use a local mock HTTP server and real scheduling simulations; they do not consume cloud tokens.

## Original EvoSpeak Parameter Profiles

Two profiles in this directory inherit the V1 infrastructure from `evospeak.params` and port the objective/scenario settings of the original EvoSpeak files:

| Profile | Objective(s) | Weights | Benchmark normalization |
|---|---|---|---|
| [multipletreegp-dynamicLLMWarmStart.params](multipletreegp-dynamicLLMWarmStart.params) | Mean weighted tardiness | 1.0 | Disabled, matching the original single-objective evaluation |
| [multipletreegp-dynamicLLMWarmStartMO.params](multipletreegp-dynamicLLMWarmStartMO.params) | Mean flow time and mean weighted tardiness | 0.8 / 0.2 | Enabled, matching `weight-objective0=0.8` and `manual-normalization=true` |

Both retain 100 individuals, 51 generations, two trees, two elites, tournament size 4, crossover/mutation/reproduction probabilities 0.80/0.15/0.05, utilization 0.85, due-date factor 1.5, 5000 recorded jobs and 1000 warmup jobs. They use V1's state, evaluator, safe weighted fitness and complete-population validation instead of the old package classes or silent random filling. The old `limit-total-evaluation-times` parameter is not a V1 stopping condition; these profiles use the generation budget. The original offline file paths are available through `evospeak.population-file`, but `evospeak.source=llm` remains active.

Both profiles now select the supplied Azure deployment `gpt-5.6-luna` on `hackathon-qh` through the Responses API. The generic `evospeak.params` keeps its OpenAI-compatible Chat Completions default. Selecting one of the Azure profiles does not change the original EvoSpeak implementation or its objective settings.

For an API key stored locally in params, this workspace also has two private companion files:

- `multipletreegp-dynamicLLMWarmStart.local.params`
- `multipletreegp-dynamicLLMWarmStartMO.local.params`

Each inherits its corresponding profile and provides an editable `llm.api-key` field. Replace its placeholder or previous credential with the same Azure resource's Key1/Key2, without quotes or a `Bearer ` prefix. Keep any required proxy settings when updating credentials. These `.local.params` files are ignored by Git and must not be shared or force-added. The tracked profiles contain an empty key field and are safe to share. On a fresh checkout, create a local companion with the same two-line structure, for example:

```properties
parent.0 = multipletreegp-dynamicLLMWarmStartMO.params
llm.api-key = REPLACE_WITH_YOUR_API_KEY
```

In an IntelliJ **EvoSpeakMain** run configuration, set **Program arguments** to the desired private file:

```text
-file src/mengxu/algorithm/OnlineEvoSpeak/multipletreegp-dynamicLLMWarmStartMO.local.params
```

For **GPMain**, change the selected parameter-file line to that same path; `GPMain` continues to assign the seed/run ID and result filenames. To run from PowerShell:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\OnlineEvoSpeak\multipletreegp-dynamicLLMWarmStart.local.params
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\OnlineEvoSpeak\multipletreegp-dynamicLLMWarmStartMO.local.params
```

Use `RuleAnalysisMain` with the same selected profile to find results under its `runs/single-objective` or `runs/multi-objective` directory. Selecting the generic default params does not automatically switch to one of these profiles or load a private companion file.

## Run Through GPMain

`src/yimei/jss/gp/GPMain.java` now selects OnlineEvoSpeak by default alongside the existing commented algorithm options. Configure `firstRunId`, `lastRunId` (inclusive), `maxTests` (maximum ID) and `resultsDirectory` in that launcher. The default range remains a single run, ID 22; to run IDs 0 through 29, set the first/last IDs to 0 and 29. Changing this range can trigger a separate, billable LLM population-generation workflow for every run.

For each ID, `GPMain` creates fresh arguments with `seed.0=<id>` and an absolute `stat.file=<resultsDirectory>/job.<id>.out.stat`. It detects the configured OnlineEvoSpeak state and invokes `EvoSpeakMain` so LLM generation and feasibility checks still happen before GP. Other evolution states continue through the original `GPRun` path. To smoke-test this launcher without a model, select `src/mengxu/algorithm/OnlineEvoSpeak/smoke.params` instead of the normal OnlineEvoSpeak params.

The common GP result files are:

```text
<resultsDirectory>/job.<id>.out.stat
<resultsDirectory>/job.<id>.time.csv
<resultsDirectory>/job.<id>.timeSumGen.csv
```

`resultsDirectory` defaults to the working directory, matching the original launcher. The timing CSV headers are `Gen,Time` and `Gen,timeSumGen`; times are seconds and the cumulative total resets for each ID. They measure GP generation work, excluding initial LLM generation and warm-start validation.

LLM prompts, populations, explanations, validation evidence and JSON metrics stay grouped in `evospeak.output-directory/job.<id>-<unique-suffix>/`. The suffix preserves separate attempts with the same seed. Its `status.json` records the ID and absolute locations of the statistics, timing files and artifact directory. Standalone `EvoSpeakMain` also uses `seed.0` as its run ID: with the default `stat.file=$out.stat`, the three common result files are placed inside that artifact directory. An explicit `stat.file` is honored using ECJ path resolution, with timing CSVs written beside it.

OnlineEvoSpeak never replaces existing statistics or timing files. When `GPMain` finds any of the three `job.<id>` result files already in `resultsDirectory`, it prints a notice and places all three new result files inside a fresh `evospeak.output-directory/job.<id>-<unique-suffix>/` artifact directory instead. The requested run ID and random seed stay unchanged, and the old files remain untouched. For example, rerunning ID 22 does not require deleting `job.22.out.stat` or changing the seed. The console and `status.json` show the new output location.

Direct `EvoSpeakMain` runs with an explicit conflicting `stat.file` still fail rather than silently redirect that requested path. Choose another path or leave the default `stat.file=$out.stat` to use an isolated artifact directory. Caller configuration and base launcher arguments are not mutated between runs. A result-file conflict is checked before population generation; reaching that check means the credential was found locally, not that the provider has authenticated it.

## LLM Configuration

All provider URLs are full endpoints, not just a host. Configure a model supported by your account or deployment. A nonempty `llm.api-key` in the selected private `.local.params` file takes precedence over the environment variable named by `llm.api-key-env`. An empty field or the `REPLACE_WITH_YOUR_API_KEY` placeholder falls back to that environment variable; the placeholder is never sent as a credential. The loader extracts the local key before ordinary ECJ parameter access and removes it from the parameter database, including parent databases, so ECJ tracing/dumps do not expose it. Status files, prompts and reports do not include the key.

Keep actual tokens out of tracked params, source, URLs, command-line arguments and shared IDEA run configurations. `.gitignore` protects only the local filename pattern, not arbitrary files or forced Git additions. Environment variables remain an alternative that avoids storing tokens in plaintext files. After changing persistent Windows environment variables, restart VS Code/IntelliJ so its launched JVM inherits them. No restart is needed when editing a selected local params file between runs.

| Provider | `llm.provider` | Endpoint | Authentication |
|---|---|---|---|
| OpenAI or compatible service | `openai-compatible` | Default `https://api.openai.com/v1/chat/completions`, or `/v1/responses` with `llm.api=responses`; override for another service | Bearer token from local params or the named environment variable |
| Azure OpenAI v1 | `azure-openai` | Full `https://<resource>.services.ai.azure.com/openai/v1/responses` with `llm.api=responses`; `.openai.azure.com` resource endpoints are also supported | Azure resource key in `api-key`; `llm.api-key-env=AZURE_OPENAI_API_KEY` |
| Azure OpenAI preview Responses | `azure-openai` | Full `https://<resource>.cognitiveservices.azure.com/openai/responses?api-version=2025-04-01-preview` with `llm.api=responses` | Same resource's key in `api-key`; `llm.api-key-env=AZURE_OPENAI_API_KEY` |
| Azure OpenAI legacy Chat Completions | `azure-openai` | Full `https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=<supported-version>` with `llm.api=chat-completions` | Azure resource key in `api-key` |
| Anthropic | `anthropic` | Default `https://api.anthropic.com/v1/messages` | `x-api-key`; set `llm.api-key-env=ANTHROPIC_API_KEY` |
| Local Ollama | `ollama` | Default `http://localhost:11434/api/chat` | No key required; start Ollama and install the model first |

Example for an OpenAI-compatible API:

```properties
llm.provider = openai-compatible
llm.api = chat-completions
llm.model = gpt-4.1-mini
llm.api-key =
llm.api-key-env = OPENAI_API_KEY
llm.proxy = system
llm.connect-timeout-seconds = 20
llm.timeout-seconds = 120
llm.max-output-tokens = 8000
llm.max-attempts = 3
```

For Chat Completions providers requiring `max_completion_tokens`, set `llm.token-parameter=max_completion_tokens`. Responses mode always maps `llm.max-output-tokens` to `max_output_tokens`, including the model's reasoning-token budget. Temperature is omitted unless `llm.temperature` is explicitly set, because some models do not support it. Anthropic and Ollama retain their existing adapters with the default `llm.api=chat-completions`. Streaming, tool calls and image generation are not supported by this rule client.

### Supplied Azure Responses Deployment

The two original-derived profiles already contain this public configuration:

```properties
llm.provider = azure-openai
llm.api = responses
llm.model = gpt-5.6-luna
llm.model-version =
llm.endpoint = https://hackathon-qh.cognitiveservices.azure.com/openai/responses?api-version=2025-04-01-preview
llm.api-key =
llm.api-key-env = AZURE_OPENAI_API_KEY
```

`llm.model-version` records an optional declared deployment version as `configuredModelVersion` in the run status. It is blank because the new deployment's model version has not been supplied; the previous deployment's version must not be reused without verification. This field does not pin or verify the live version, and it is not sent as `api-version` or `model_version`. The deployed version is managed in Azure. The supplied `2025-04-01-preview` value is an API version, not a model version.

Unlike the Python SDK's `base_url`, `llm.endpoint` is the full POST URL and must include `/responses`. The configured preview endpoint requires its `api-version` query, which the client preserves. Azure v1 endpoints remain supported without a dated query. `llm.model` is the deployment name. The client sends `instructions`, `input` and `max_output_tokens`, with storage, streaming and background mode disabled. It extracts assistant `output_text` blocks in order, skips reasoning items, and rejects incomplete, truncated, filtered, refused or malformed responses before they reach rule validation. Both population generation and `RuleAnalysisMain` use this adapter.

The supplied Python example uses `get_bearer_token_provider(DefaultAzureCredential(), "https://ai.azure.com/.default")`, which is **Microsoft Entra ID authentication**, not a resource API key. This Java configuration uses the resource's **Key1/Key2** in the `api-key` header and requires key authentication to be enabled for that resource. Do not paste the token-provider expression or a temporary Entra access token into this key field. Automatic Entra token acquisition/refresh is not implemented; Entra-only access requires a separate authentication adapter.

The selected single-objective private profile inherits this Azure configuration. Update its local key field when rotating credentials, keep any required local proxy setting, rebuild the project in IDEA, and run `GPMain` as before. For weighted two-objective runs or rule analysis, select the corresponding private profile explicitly. A one-request Java test on 2026-09-15 returned HTTP 200 and the requested `OK` text from this deployment. This confirms that minimal inference worked at that time; it does not verify a full 100-individual population, future quota or scheduling quality. Local regression tests cover rule validation, actual small GP runs and independent analysis with mocked Responses output.

Only HTTPS endpoints are accepted except loopback HTTP for local services. Redirects are not followed. Transient transport/429/5xx failures have a bounded retry count; authentication and other client errors fail immediately. Truncated model responses are rejected with instructions to reduce batch size or increase output tokens.

## Network and Proxy Setup

`ConnectException: Connection timed out` means the Java process could not establish a connection to the API endpoint or proxy. It is not an API-key rejection: no usable HTTP response has been received yet. Browser connectivity or IDEA's own HTTP proxy settings do not establish connectivity for the application's JVM. Java's HTTP client does not automatically interpret `HTTP_PROXY`/`HTTPS_PROXY` environment variables.

Configure the route explicitly in the selected private params file when needed:

```properties
llm.proxy = http://127.0.0.1:7890
llm.connect-timeout-seconds = 20
llm.timeout-seconds = 120
```

Port 7890 is an example, not a universally correct default. Use the running proxy application's **HTTP or mixed** port, not a SOCKS-only port. The proxy must support HTTP CONNECT for HTTPS destinations. Keep the proxy application running while generating or analyzing rules. The original TLS certificate verification remains enabled.

Supported `llm.proxy` values:

- `system` (default): use the JVM's default `ProxySelector`. For JVM properties such as `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890`, configure IDEA's **VM options** before launch. Reading Windows system settings via `-Djava.net.useSystemProxies=true` also requires JVM startup configuration and enabled system proxy settings; it is not the same as configuring IDEA's proxy UI.
- `direct`: force a direct connection, bypassing the JVM proxy selector.
- `http://host:port`: always use that explicit HTTP proxy. Proxy credentials, SOCKS URLs, non-root paths, query strings and fragments are rejected. Proxy authentication is not implemented; a 407 response is a proxy issue rather than an LLM API-key issue.

`llm.connect-timeout-seconds` limits connection establishment independently of `llm.timeout-seconds`, which bounds the overall request including the model response. Increasing model response time cannot fix an unavailable network route. Diagnostics distinguish TCP/connect failures, DNS failures, TLS failures and request timeouts, and include the endpoint host/port, selected route and timeout settings without credentials or prompt content. Repair trust-store/certificate issues instead of disabling TLS verification.

### Check Connectivity Without LLM Generation

From the repository root, this uses the same Java proxy/TLS settings but sends only one **unauthenticated HEAD request** to the configured endpoint:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action check -ParamsFile .\src\mengxu\algorithm\OnlineEvoSpeak\multipletreegp-dynamicLLMWarmStart.local.params
```

To check a public endpoint without loading a private file:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action check -Overrides @('llm.proxy=http://127.0.0.1:7890','llm.connect-timeout-seconds=5','llm.timeout-seconds=10')
```

In IDEA, use an **EvoSpeakMain** configuration with program arguments `--check-connection -file <selected-params-file>`. This option belongs to `EvoSpeakMain`, not the batch `GPMain` entry point. It does not send an API key or prompt, request model generation, or create a GP run. A response such as HTTP 401/403/404/405 confirms that an HTTP response was received; it does not prove that the key, model, account or requested inference endpoint is authorized. Resolve response-specific authorization problems separately after transport works.

For the reported workstation on 2026-09-14, Windows system proxy settings were disabled and Java direct access timed out. An explicit HTTP proxy at `127.0.0.1:7890` returned HTTP 404 over HTTPS in about two seconds. Only that verified proxy setting was added to the selected single-objective `.local.params`; the key was not printed or sent. Other machines/profiles must use their own actual reachable proxy or direct route.

### HTTP 401 and Credential Checks

An inference request returning HTTP 401 has received a server response but failed authentication. Check the service that issued the key before changing proxy or GP settings. `api.openai.com` requires an official OpenAI API key; Azure resource keys, third-party gateway keys, another provider's tokens and ChatGPT login/session credentials are not interchangeable. For a compatible third-party API, use that provider's documented full chat-completions URL and supported model instead of sending its token to the official OpenAI host.

A nonempty `llm.api-key` in the selected private params takes precedence over the environment key. Replacing only `OPENAI_API_KEY` or `AZURE_OPENAI_API_KEY` will not change the credential being sent while a local key is present. Enter the full active credential for the selected provider without quotes or a `Bearer ` prefix; the client adds the correct authentication header. Azure resource-key mode requires Key1/Key2 from that same resource, not an Entra access token. Revoke/rotate an invalid or deactivated key at its issuing service, then update the private file. Never paste keys into chat, logs or committed files. This code does not automatically switch credentials after a rejection.

401 diagnostics include the endpoint host, credential source and recognized standard error codes such as `invalid_api_key` or `authentication_error`. They suppress the raw response message and unknown code/type fields because providers can echo the token there. A 407 is proxy authentication and is reported separately. Neither response triggers generation retries.

For an OpenAI-compatible or Azure v1 endpoint ending in `/chat/completions` or `/responses` without query parameters, `run.ps1 -Action auth` or **EvoSpeakMain** with `--check-auth -file <selected-params-file>` sends one authenticated **GET** to the same service's `/models` endpoint (`/openai/v1/models` for Azure v1). It uses the same proxy and headers as generation, with no prompt, inference request or GP run. The key is sent only in the provider's authentication header and is not printed. Unlike `--check-connection`, this check requires the configured credential. A successful model-list response does not prove access to a particular inference model or available billing quota. HTTP 403/404/405 can indicate restricted model-list permissions or an unsupported route, so those outcomes alone do not establish inference-token validity.

The currently configured `hackathon-qh` preview URL is **not supported by `--check-auth`**; the check deliberately does not guess a different API or rewrite a versioned endpoint. Use `--check-connection` for network-only diagnostics. Verifying inference access on this preview endpoint requires an explicitly requested, bounded text-generation test, which can consume tokens. Legacy Azure deployment URLs and Anthropic configurations are also unsupported by the model-list check.

During investigation of the reported 401 on 2026-09-14, a single read-only check against the configured official OpenAI model-list endpoint returned `invalid_api_key`. Local format checks found no quotes, Bearer prefix or whitespace. The token's issuing service could not be confirmed, so no key or endpoint was changed. A valid token for the selected service is required to proceed.

## Objectives and Weights

Use the `evospeak.*` keys; the launcher maps them to ECJ fitness and evaluation-model dimensions consistently.

```properties
evospeak.objective-mode = multi
evospeak.objective.0 = mean-flowtime
evospeak.objective.1 = mean-weighted-tardiness
evospeak.weight.0 = 0.3
evospeak.weight.1 = 0.7
evospeak.normalization = true
```

`multi` means a weighted sum of two minimized objectives, matching the original EvoSpeak method. It is not NSGA-II and does not claim to evolve a Pareto front in a single run. Both raw objective values remain in the metrics. Finite nonnegative weights are normalized to sum to one; all-zero weights are rejected. Zero weights are supported without producing `0 * Infinity = NaN`.

For a single objective:

```properties
evospeak.objective-mode = single
evospeak.objective.0 = mean-flowtime
```

Only objective 0 is evaluated and its weight is 1. Supported objective names come from `yimei.jss.jobshop.Objective`. Typical alternatives include `mean-tardiness`, `max-flowtime`, `max-weighted-flowtime`, `max-tardiness`, and `max-weighted-tardiness`.

With normalization enabled, each objective is divided by the current scenario's benchmark-rule score, recomputed under the current training seed. A zero benchmark is replaced with denominator 1; negative/non-finite benchmarks stop the run. With normalization disabled, the score is the weighted sum of raw objectives. Every generation reevaluates all individuals, including elites and reproductions, so rotated seeds do not leave stale fitness values.

## Example-Guided Warm Starts

The exact first-batch messages can be exported for manual offline use, with no LLM connection or GP run:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action prompt -ParamsFile .\src\mengxu\algorithm\OnlineEvoSpeak\multipletreegp-dynamicLLMWarmStartMO.params -ExtraArgs @('.\out\offline-multi-prompt.md')
```

This invokes `EvoSpeakMain --export-prompt <new-output.md>` and uses the same shared system prompt and `PopulationFactory.generationPrompt` as online requests. Override objectives, weights, batch size or `evospeak.prompt-file` through the usual params/`-Overrides` mechanism. Existing files are not overwritten. [Offline instructions and complete single/multi-objective prompt snapshots](../OfflineEvoSpeak/README.md#exact-shared-prompts) explain manual batches and local conversion from the LLM's response to ECJ TXT.

The default generation prompt now follows the supplied **Insights Extraction -> New Heuristic Generation -> Expected Objective Effects** workflow. `warm-start-examples.json` contains the five input heuristics from that prompt, not the 100 individuals in the pasted model answer. Each example retains its reported scalar score as unverified historical metadata. Those scores are not comparable training observations without their original scenario, seeds and normalization, and are never loaded into GP fitness.

```properties
evospeak.examples-file = warm-start-examples.json
evospeak.max-examples = 10
evospeak.generation-language = English
```

The reference file can be a standalone ECJ population file or JSON with an `individuals` array. JSON individuals require `sequencing` and `routing` strings and may include a finite nonnegative `reportedFitness`. Use a small set of selected reference pairs, not a document combining a prompt, prose and a model answer. A missing/malformed reference file or too many examples stops before any generation request. An empty `evospeak.examples-file` disables references. Offline `evospeak.source=file` does not load or require examples.

References are parsed with the actual terminal/function grammar and bounded to depth 32 and 2047 nodes per tree. This allows source heuristics to be more complex than newly generated trees, which still obey `evospeak.max-tree-depth` and `evospeak.max-tree-nodes`. Reference parsing does not establish performance or simulation feasibility. Whole reference pairs are excluded from new LLM-generated populations; reusable substructures are encouraged.

Each batch prompt includes the five canonical pairs, terminal definitions, the real scheduling scenario, configured objective weights and the exact raw or benchmark-normalized score. Both objective modes share the supplied `# Prompt`, `Provided Information`, `Tasks` and `Output Requirements` structure and present the same references between `<START>` and `<END>` in ECJ style. Only objective-related wording and the fitness dimension differ. Historical expressions and scalar scores are preserved; only their placeholder display encodings are replaced by valid ECJ encodings. The model must discuss context-dependent terms and cancellation rather than assume, for example, that additive `MWT` changes job ordering within one machine queue or that `TIS` directly encodes a due date. It must propose varied strategies, not just a long series of terminal substitutions. Exact duplicates/copies are checked in code; semantic novelty and explanation correctness still need scientific review.

To use the supplied raw `lambda1 * Fmean + lambda2 * WTmean`, where `lambda2 = 1 - lambda1`, disable benchmark normalization. For example, with equal weights:

```properties
evospeak.objective-mode = multi
evospeak.objective.0 = mean-flowtime
evospeak.objective.1 = mean-weighted-tardiness
evospeak.weight.0 = 0.5
evospeak.weight.1 = 0.5
evospeak.normalization = false
```

The existing normalization default is unchanged; the supplied prompt does not specify numerical weights. Different weights or single-objective settings are carried into the prompt, not overwritten with 0.5/0.5. When normalization is enabled, the MO prompt explicitly distinguishes the supplied raw formula from the actual benchmark-normalized GP score. `Fmean` and `WTmean` are aliases for the corresponding configured objectives.

### Multi-Objective Reply

The multi-objective system and user prompts request a text response, not JSON-only content. The prompt gives this exact layout with the current batch count. One illustrative pair is:

```text
## Insights Extraction
- MWT can be constant within a sequencing queue, so subtracting W favors larger job weights for finite values. (reference IDs: [1])

## New Heuristics
<START>
Number of Individuals: i1|
Individual Number: i0|
Evaluated: F
Fitness: [d0|0.0| d0|0.0|]
Tree 0:
(/ PT W)
Tree 1:
(+ WIQ TRANT)
Sequencing: PT/W favors short operations and larger positive job weights.
Routing: WIQ+TRANT balances queued work and transport time.
Expected objective trade-off: Both objectives may benefit, but the effect depends on congestion and due dates and requires testing.
Reference IDs: [1]
<END>
```

Use the exact headings, field labels, contiguous zero-based indices and one `<START>/<END>` block. Counts must match the requested batch. The parser also accepts one enclosing Markdown fence and older JSON responses in either mode for compatibility. `RulePopulation.generationObject` extracts insights, rule strings, explanations and citations into the existing validation structure. It never uses the model's fitness field as training fitness. The original reply is saved as `generation-response-<batch>.txt`; the validated `generated-population.txt` contains only native ECJ population records, without prose.

### Single-Objective Reply

The single-objective prompt uses the same template, system message, references and output structure as MO, changing only the target description and related wording. With the supplied single-objective params it minimizes raw mean weighted tardiness (`WTmean`), not a weighted combination with `Fmean`. Other configured `evospeak.objective.0` values and normalization settings are respected. The reply uses one fitness value and `Expected objective effect:`:

```text
## Insights Extraction
- MWT can be constant within a sequencing queue, so subtracting W favors larger job weights for finite values. (reference IDs: [1])

## New Heuristics
<START>
Number of Individuals: i1|
Individual Number: i0|
Evaluated: F
Fitness: [d0|0.0|]
Tree 0:
(/ PT W)
Tree 1:
(+ WIQ TRANT)
Sequencing: PT/W favors short operations and larger positive job weights.
Routing: WIQ+TRANT balances queued work and transport time.
Expected objective effect: Prioritizing important jobs and limiting queue delays may reduce WTmean, but the effect depends on due dates and congestion and requires testing.
Reference IDs: [1]
<END>
```

For output-file and older JSON compatibility, the extracted objective explanation remains stored under `explanation.objectiveTradeOff` in both modes; for single objective its content describes the effect on that one objective.

This one-pair example shows the schema, not a complete 100-individual response. Actual responses must contain exactly the requested batch count, 1-12 nonempty insights (up to 2000 characters each), and three nonempty explanation fields per pair (up to 4000 characters each). Reference IDs are zero-based, distinct and must exist in the supplied set; when references are disabled, use empty ID arrays. Insights and explanations are requested in the same content call, with no additional insight-only request.

Malformed batch structure/count/insights rejects the batch. Missing required labels or malformed record structure in a text reply also reject the batch before parsing trees. Semantically invalid explanation fields/citations or rules reject the affected pair during review. All failures are retained as corrective feedback for replacement batches, within the same bounded generation budget. Accepted rules and their explanations stay aligned after candidate rejection. The native ECJ population remains in the original `Number of Individuals`, `Individual Number`, `Evaluated: F`, `Fitness`, `Tree 0` and `Tree 1` style, serialized by ECJ itself.

Completed online generation also produces `warm-start-report.md` with bullet-point insights and the validated rule pairs plus sequencing, routing and expected-objective explanations. `reference-heuristics.json` snapshots the exact reference facts sent to the model; `generated-rules.json` preserves the explanations, task score and batch insights. Both generated population formats remain readable by the existing offline and independent analysis tools. These narrative fields are LLM interpretations, not measured performance or a proof of feasibility.

## Generation and Feasibility

```properties
evospeak.source = llm
evospeak.run-gp = true
evospeak.batch-size = 10
evospeak.max-batches = 40
pop.subpop.0.size = 100
evospeak.max-tree-depth = 8
evospeak.max-tree-nodes = 255
evospeak.validation.jobs = 500
evospeak.validation.warmup = 100
evospeak.validation.seeds = 17001,17002
evospeak.validation.timeout-seconds = 20
evospeak.validation.max-decisions = 1000000
```

The supported grammar uses the actual `relative` terminals and binary `+`, `-`, `*`, `/`, `Min`, `Max` nodes. Validation rejects unknown terminals/functions, incorrect arity, missing/extra parentheses, trailing expressions, overlarge trees, repeated pairs, exact copies of reference pairs in online generation, non-finite priorities, invalid scheduling objectives, or exhausted simulation budgets. The two trees must both be exercised.

Feasible means feasible on these bounded validation scenarios, not a mathematical guarantee for every possible future queue, seed or GP offspring. Use larger validation workloads/seeds for stronger screening. Validation uses separate simulations and does not cache validation fitness as training fitness.

At defaults, generation permits at most 40 content batches, each with at most 3 HTTP attempts. Cloud usage is billable; tune batch/count/token budgets before large runs. Optional `evospeak.prompt-file` supplies additional generation instructions. `evospeak.run-gp=false` generates, validates and writes the complete TXT without initializing a GP population or creating GP statistics/timing/generation files. It still calls the LLM when `evospeak.source=llm`.

### TXT Before GP

Both objective profiles save the same standalone layout used by [the original population example](../OfflineEvoSpeak/WarmStart/population_100_0.8_MO.txt). For a 100-individual multi-objective run, the beginning has this form (one illustrative record shown):

```text
Number of Individuals: i100|
Individual Number: i0|
Evaluated: F
Fitness: [d0|0.0| d0|0.0|]
Tree 0:
 (/ PT W)
Tree 1:
 (+ WIQ TRANT)
```

The file contains exactly `pop.subpop.0.size` records, numbered from zero. Every individual has both trees: Tree 0 is sequencing and Tree 1 is routing. The single-objective fitness record has one encoded value (`Fitness: [d0|0.0|]`); the two-objective record has two. These zeros mean unevaluated fitness, not measured scheduling results. ECJ writes the encoding; the LLM supplies rules and explanations, not fabricated fitness values. All individuals are evaluated afresh by GP.

The default profile output locations, relative to the repository root, are:

```text
src/mengxu/algorithm/OnlineEvoSpeak/runs/single-objective/job.<seed>-<suffix>/generated-population.txt
src/mengxu/algorithm/OnlineEvoSpeak/runs/multi-objective/job.<seed>-<suffix>/generated-population.txt
```

The launcher prints the absolute TXT path before `Initializing Generation 0`. It then sets `pop.subpop.0.file` to that path and loads the saved individuals through ECJ, rather than replacing a random population with an in-memory list. There is no random fill or wrapping of a short input; a size mismatch stops evolution. The original EvoSpeak example file is not overwritten. `status.json` records `initialPopulationFile` and whether the training state actually loaded it (`initialPopulationLoaded`).

The training random generator starts from the configured run seed independently of LLM preparation. Removing the old, discarded random initialization changes the consumed random-number sequence relative to older V1 runs; do not expect identical later generations across those versions solely from an identical seed.

Offline reuse accepts the original standalone ECJ text, a complete annotated reply from either objective mode in the format above, or `{"individuals":[{"sequencing":"PT","routing":"WIQ"}]}` JSON. An annotated reply must pass through validation/serialization before use by the native ECJ loader, because it contains explanatory text:

```properties
evospeak.source = file
evospeak.population-file = ../OfflineEvoSpeak/WarmStart/population_100_0.5_MO.txt
```

Old serialized fitness values are ignored. Accepted expressions are converted to correctly constrained GP individuals with fresh fitness and `Evaluated: F`. Offline reuse also writes a validated TXT in the new run directory and starts GP from that file. Use `evospeak.population-file` for input, not ECJ's full-population `pop.file`; the launcher manages `pop.subpop.0.file` after validation. Offline inputs with insufficient distinct validated pairs fail rather than fabricate replacements; switch back to `source=llm` for automated generation.

Relative OfflineEvoSpeak/analysis file paths are resolved relative to the params file. Each run uses an ID-labelled `job.<seed>-<unique-suffix>` directory under `runs`. Statistics paths follow ECJ conventions; `GPMain` supplies an absolute path in its selected results directory.

## Independent Rule Analysis

Run `RuleAnalysisMain.main()` without training. With no population path, it chooses the newest generated population. To focus on a small selection:

```powershell
.\src\mengxu\algorithm\OnlineEvoSpeak\run.ps1 -Action analyze -ExtraArgs @('--population','path\to\generated-population.txt','--indices','0,1','--prompt','Explain the congestion and urgency trade-off.','--output','path\to\report.md')
```

Alternatively use `--prompt-file` for a UTF-8 prompt document. Params equivalents are `analysis.population-file`, `analysis.indices`, `analysis.prompt`, `analysis.prompt-file` and `analysis.output-file`. `analysis.language=Chinese` is the default. `analysis.max-individuals` caps the number analyzed; each selected valid pair uses one content request, with at most `analysis.max-attempts` schema attempts (default 2), each subject to the HTTP retry budget.

Reports contain:

- Exact sequencing/routing expressions, depths and node counts.
- Per-tree terminal occurrence tables grounded in `JobShopAttribute.value`, not guessed from abbreviations.
- Separate natural-language sequencing, routing, interaction and limitations sections.
- Structural notes about cancellation or identical numerator/denominator subtrees.
- The user prompt and model identity for traceability.

Prompts explain that lower priority wins, same-job routing ties keep the first option, `W` is job importance rather than objective preference, `MWT`/`rDD`/`SL` can be negative, and machine-level terms may be constant in a sequencing queue. `Div.eval` checks positive zero with `Double.compare` and returns 1; negative zero is not protected by that exact implementation. Non-finite results are rejected by validation.

Invalid expressions are marked in the report without being sent to the LLM. Interrupted/failed reports are explicitly marked INCOMPLETE. Existing output files are not overwritten. Natural-language interpretations remain model-generated and should be checked before use in a paper; the tool does not invent experimental evidence.

## Outputs

Each completed artifact directory contains `status.json`, `validation-report.json`, `generated-population.txt` and `generated-rules.json`. Online runs save generation prompts/responses and a `reference-heuristics.json` snapshot. Completed online generation also saves `warm-start-report.md`; generation insights remain in the batch records of both the validation report and enriched rules JSON. Failed validation does not publish a complete population or warm-start report. GP runs add `generations.jsonl`, `final-population.txt` and `best-rules.txt` (best in the final generation), plus the `job.<id>.out.stat`, `job.<id>.time.csv` and `job.<id>.timeSumGen.csv` files at the result paths described above. The statistics log separately contains ECJ's best-of-run record.

`status.json` stores the run ID, result paths (including `initialPopulationFile`), `initialPopulationLoaded`, objective settings, configured weights, normalized weights, scenario, validation controls, seed, provider/model and completion state, without API keys. The state reaches `population-ready` after publication, then `completed` after successful GP, or `validated` for generate-only mode. Failures use `failed`. `generations.jsonl` stores the run ID, raw objectives, scalar fitness, normalization denominators, elapsed/cumulative time and the current best rule pair. An inactive objective that becomes non-finite after breeding is represented as JSON null rather than invalid JSON.

The default `runs` directory is Git-ignored because prompts, responses and experiment artifacts can be large or sensitive. Archive a run directory explicitly when needed for reproducibility. LLM generation is not guaranteed deterministic; reuse the saved population with the same GP seed for a controlled replay.

## Offline Implementation Notes

The original OfflineEvoSpeak Java files remain an offline workflow, with these compatibility considerations:

- Source packages and params now use `mengxu.algorithm.OfflineEvoSpeak.WarmStart` and agree with the renamed directory; rebuild existing IDEA configurations accordingly.
- Default population paths are relative historical example filenames. Supply a validated TXT for actual runs; the legacy utility helpers still contain machine-specific paths.
- The example population uses fabricated, single-value fitness records although the MO configuration expects two objectives. V1 discards those fields and writes native ECJ serialization.
- The original MO state writes `weights[1]` unconditionally, and initializes weighted fitness only inside its normalization branch. Single-objective or normalization-disabled use is unsafe.
- `LLMWarmStart` is a text-processing stub; it does not implement a network generation/validation/retry workflow. The separate old `LLMcode` utility is not a configurable LLM API adapter.

The supplied `population_100_0.5_MO.txt` has 100 pairs that pass strict depth-8 syntax parsing in the current grammar. Syntax validity alone does not establish scheduling feasibility, diversity or performance. V1's offline mode applies the same simulation gate to these rules as to online-generated rules.

## Verification

The regression runner covers weighted/single-objective scoring, normalization-disabled operation, strict parsing, the original file format, native ECJ serialization round-trip, real bounded simulation validation, all four provider envelopes, authentication/missing-key/truncation failures, rejection feedback, GP blocking on insufficient valid individuals, automatic real GP runs, and independent report generation. Example-guided checks cover the supplied five references, unequal/single-objective prompt settings, raw versus normalized score descriptions, invalid citations, empty/missing insights and explanations, wrong batch counts, exact reference copying, malformed reference files, example-free generation and explanation alignment after retries. Launcher checks run two IDs through `GPMain`, verify `GPRun` fallback, isolate arguments/configuration and timing totals, honor explicit result paths, and reject existing result-file collisions.

The single- and multi-objective Responses workflows explicitly forbid random-individual initialization, verify that the TXT exists before ECJ loads any individual, compare every loaded tree with the saved file, and check the native fitness dimensions and unevaluated flags. Both modes cover successful two-generation evolution, generate-only publication without GP statistics, and incomplete generation without a partial population or GP initialization.

Provider regression tests use a local mock server and fake credentials, while GP and scheduling checks use the real Java implementation. Network checks exercise explicit HTTP proxies, JVM proxy selection, direct bypass, malformed proxy settings, categorized errors and the key-free HEAD entry point. Authentication checks cover header formatting, known error-code filtering without leaking echoed credentials, no retry on 401, and the authenticated GET check's 200/401/403/404 outcomes without starting GP. The earlier minimal successful Azure call is documented above; it is separate from these local workflow regressions and does not establish complete LLM-population generation or model quality. The offline smoke configuration completes three generations without a model. Full-size research experiments and claims of performance improvement are outside these smoke checks.