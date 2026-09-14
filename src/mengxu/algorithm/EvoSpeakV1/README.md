# EvoSpeakV1

An independently runnable derivative of EvoSpeak's offline LLM warm start. It retains dual-tree GP, tournament selection, crossover/mutation/reproduction, and weighted scheduling objectives. The original `EvoSpeak/WarmStart` files and existing experiments are not modified.

The workflow is:

1. Load the scheduling scenario, objective mode, weights and LLM settings from params.
2. Load the provided reference heuristics and ask the selected LLM to extract terminal-grounded insights, then generate a small JSON batch of new pairs with explanations of their expected objective trade-offs.
3. Check the response schema and reference IDs, parse against the actual ECJ GP grammar, enforce tree limits, reject duplicate/reference-copy pairs, and run bounded scheduling simulations on validation seeds.
4. Feed rejection evidence back to the LLM and request replacements within the configured budget.
5. Publish a correctly serialized ECJ population only when the required population is complete, then run GP automatically.

No generated Java/Python/shell code is executed. LLM text is treated as expression data. Failed generation, invalid credentials, malformed output or insufficient feasible individuals prevent GP from starting. There is no silent random-population fallback.

## Quick Start

Requirements: JDK 11+, the existing `libraries` JARs, and PowerShell 7 for the convenience script. Tested with OpenJDK 26.0.1; the script targets Java 11 bytecode. `JAVA_HOME`, a JDK on PATH, or a JDK under the user's `.jdks` directory is detected automatically.

In VS Code, select one of these Run and Debug configurations:

- **EvoSpeakV1: Offline Smoke**: no API key required; two handwritten example rules, three GP generations.
- **EvoSpeakV1: LLM Generate and Train**: uses `evospeak.params` and automatically calls the configured LLM.
- **EvoSpeakV1: Explain Latest Population**: analyzes the most recent generated population using the configured LLM.

The pre-launch task compiles the required source and reflection-loaded ECJ classes. In IntelliJ, run `EvoSpeakMain.main()` or select EvoSpeakV1 in `GPMain.main()` with the repository root as the working directory and `libraries/*.jar` on the classpath. Running `GPRun` directly is not the V1 entry point because it bypasses generation configuration.

From the repository root:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\smoke.params
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action test
```

The first command checks the local environment without a model. The second needs the LLM configuration below. The regression tests use a local mock HTTP server and real scheduling simulations; they do not consume cloud tokens.

## Original EvoSpeak Parameter Profiles

Two profiles in this directory inherit the V1 infrastructure from `evospeak.params` and port the objective/scenario settings of the original EvoSpeak files:

| Profile | Objective(s) | Weights | Benchmark normalization |
|---|---|---|---|
| [multipletreegp-dynamicLLMWarmStart.params](multipletreegp-dynamicLLMWarmStart.params) | Mean weighted tardiness | 1.0 | Disabled, matching the original single-objective evaluation |
| [multipletreegp-dynamicLLMWarmStartMO.params](multipletreegp-dynamicLLMWarmStartMO.params) | Mean flow time and mean weighted tardiness | 0.8 / 0.2 | Enabled, matching `weight-objective0=0.8` and `manual-normalization=true` |

Both retain 100 individuals, 51 generations, two trees, two elites, tournament size 4, crossover/mutation/reproduction probabilities 0.80/0.15/0.05, utilization 0.85, due-date factor 1.5, 5000 recorded jobs and 1000 warmup jobs. They use V1's state, evaluator, safe weighted fitness and complete-population validation instead of the old package classes or silent random filling. The old `limit-total-evaluation-times` parameter is not a V1 stopping condition; these profiles use the generation budget. The original offline file paths are available through `evospeak.population-file`, but `evospeak.source=llm` remains active.

For an API key stored locally in params, this workspace also has two private companion files:

- `multipletreegp-dynamicLLMWarmStart.local.params`
- `multipletreegp-dynamicLLMWarmStartMO.local.params`

Each inherits its corresponding profile and contains `llm.api-key=REPLACE_WITH_YOUR_API_KEY`. Replace that placeholder locally with your actual token, without quotes. These `.local.params` files are ignored by Git and must not be shared or force-added. The tracked profiles contain an empty key field and are safe to share. On a fresh checkout, create a local companion with the same two-line structure, for example:

```properties
parent.0 = multipletreegp-dynamicLLMWarmStartMO.params
llm.api-key = REPLACE_WITH_YOUR_API_KEY
```

In an IntelliJ **EvoSpeakMain** run configuration, set **Program arguments** to the desired private file:

```text
-file src/mengxu/algorithm/EvoSpeakV1/multipletreegp-dynamicLLMWarmStartMO.local.params
```

For **GPMain**, change the selected parameter-file line to that same path; `GPMain` continues to assign the seed/run ID and result filenames. To run from PowerShell:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\multipletreegp-dynamicLLMWarmStart.local.params
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\multipletreegp-dynamicLLMWarmStartMO.local.params
```

Use `RuleAnalysisMain` with the same selected profile to find results under its `runs/single-objective` or `runs/multi-objective` directory. Selecting the generic default params does not automatically switch to one of these profiles or load a private companion file.

## Run Through GPMain

`src/yimei/jss/gp/GPMain.java` now selects EvoSpeakV1 by default alongside the existing commented algorithm options. Configure `firstRunId`, `lastRunId` (inclusive), `maxTests` (maximum ID) and `resultsDirectory` in that launcher. The default range remains a single run, ID 22; to run IDs 0 through 29, set the first/last IDs to 0 and 29. Changing this range can trigger a separate, billable LLM population-generation workflow for every run.

For each ID, `GPMain` creates fresh arguments with `seed.0=<id>` and an absolute `stat.file=<resultsDirectory>/job.<id>.out.stat`. It detects the configured EvoSpeakV1 state and invokes `EvoSpeakMain` so LLM generation and feasibility checks still happen before GP. Other evolution states continue through the original `GPRun` path. To smoke-test this launcher without a model, select `src/mengxu/algorithm/EvoSpeakV1/smoke.params` instead of the normal EvoSpeakV1 params.

The common GP result files are:

```text
<resultsDirectory>/job.<id>.out.stat
<resultsDirectory>/job.<id>.time.csv
<resultsDirectory>/job.<id>.timeSumGen.csv
```

`resultsDirectory` defaults to the working directory, matching the original launcher. The timing CSV headers are `Gen,Time` and `Gen,timeSumGen`; times are seconds and the cumulative total resets for each ID. They measure GP generation work, excluding initial LLM generation and warm-start validation.

LLM prompts, populations, explanations, validation evidence and JSON metrics stay grouped in `evospeak.output-directory/job.<id>-<unique-suffix>/`. The suffix preserves separate attempts with the same seed. Its `status.json` records the ID and absolute locations of the statistics, timing files and artifact directory. Standalone `EvoSpeakMain` also uses `seed.0` as its run ID: with the default `stat.file=$out.stat`, the three common result files are placed inside that artifact directory. An explicit `stat.file` is honored using ECJ path resolution, with timing CSVs written beside it.

EvoSpeakV1 never replaces existing statistics or timing files. When `GPMain` finds any of the three `job.<id>` result files already in `resultsDirectory`, it prints a notice and places all three new result files inside a fresh `evospeak.output-directory/job.<id>-<unique-suffix>/` artifact directory instead. The requested run ID and random seed stay unchanged, and the old files remain untouched. For example, rerunning ID 22 does not require deleting `job.22.out.stat` or changing the seed. The console and `status.json` show the new output location.

Direct `EvoSpeakMain` runs with an explicit conflicting `stat.file` still fail rather than silently redirect that requested path. Choose another path or leave the default `stat.file=$out.stat` to use an isolated artifact directory. Caller configuration and base launcher arguments are not mutated between runs. A result-file conflict is checked before population generation; reaching that check means the credential was found locally, not that the provider has authenticated it.

## LLM Configuration

All provider URLs are full endpoints, not just a host. Configure a model supported by your account or deployment. A nonempty `llm.api-key` in the selected private `.local.params` file takes precedence over the environment variable named by `llm.api-key-env`. An empty field or the `REPLACE_WITH_YOUR_API_KEY` placeholder falls back to that environment variable; the placeholder is never sent as a credential. The loader extracts the local key before ordinary ECJ parameter access and removes it from the parameter database, including parent databases, so ECJ tracing/dumps do not expose it. Status files, prompts and reports do not include the key.

Keep actual tokens out of tracked params, source, URLs, command-line arguments and shared IDEA run configurations. `.gitignore` protects only the local filename pattern, not arbitrary files or forced Git additions. Environment variables remain an alternative that avoids storing tokens in plaintext files. After changing persistent Windows environment variables, restart VS Code/IntelliJ so its launched JVM inherits them. No restart is needed when editing a selected local params file between runs.

| Provider | `llm.provider` | Endpoint | Authentication |
|---|---|---|---|
| OpenAI or compatible service | `openai-compatible` | Default `https://api.openai.com/v1/chat/completions`; override for another service | Bearer token from local params or the named environment variable |
| Azure OpenAI | `azure-openai` | Full `https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=<supported-version>` | `api-key` header; e.g. `llm.api-key-env=AZURE_OPENAI_API_KEY` |
| Anthropic | `anthropic` | Default `https://api.anthropic.com/v1/messages` | `x-api-key`; set `llm.api-key-env=ANTHROPIC_API_KEY` |
| Local Ollama | `ollama` | Default `http://localhost:11434/api/chat` | No key required; start Ollama and install the model first |

Example for an OpenAI-compatible API:

```properties
llm.provider = openai-compatible
llm.model = gpt-4.1-mini
llm.api-key =
llm.api-key-env = OPENAI_API_KEY
llm.proxy = system
llm.connect-timeout-seconds = 20
llm.timeout-seconds = 120
llm.max-output-tokens = 8000
llm.max-attempts = 3
```

For providers requiring `max_completion_tokens`, set `llm.token-parameter=max_completion_tokens`. Temperature is omitted unless `llm.temperature` is explicitly set, because some models do not support it. The adapter supports text chat completions/messages, not every provider's Responses API or streaming API.

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
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action check -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\multipletreegp-dynamicLLMWarmStart.local.params
```

To check a public endpoint without loading a private file:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action check -Overrides @('llm.proxy=http://127.0.0.1:7890','llm.connect-timeout-seconds=5','llm.timeout-seconds=10')
```

In IDEA, use an **EvoSpeakMain** configuration with program arguments `--check-connection -file <selected-params-file>`. This option belongs to `EvoSpeakMain`, not the batch `GPMain` entry point. It does not send an API key or prompt, request model generation, or create a GP run. A response such as HTTP 401/403/404/405 confirms that an HTTP response was received; it does not prove that the key, model, account or requested inference endpoint is authorized. Resolve response-specific authorization problems separately after transport works.

For the reported workstation on 2026-09-14, Windows system proxy settings were disabled and Java direct access timed out. An explicit HTTP proxy at `127.0.0.1:7890` returned HTTP 404 over HTTPS in about two seconds. Only that verified proxy setting was added to the selected single-objective `.local.params`; the key was not printed or sent. Other machines/profiles must use their own actual reachable proxy or direct route.

### HTTP 401 and Credential Checks

An inference request returning HTTP 401 has received a server response but failed authentication. Check the service that issued the key before changing proxy or GP settings. `api.openai.com` requires an official OpenAI API key; Azure resource keys, third-party gateway keys, another provider's tokens and ChatGPT login/session credentials are not interchangeable. For a compatible third-party API, use that provider's documented full chat-completions URL and supported model instead of sending its token to the official OpenAI host.

A nonempty `llm.api-key` in the selected private params takes precedence over the environment key. Replacing only `OPENAI_API_KEY` will not change the credential being sent while a local key is present. Enter the full active token without quotes or a `Bearer ` prefix; the client adds the correct authentication header. Revoke/rotate an invalid or deactivated key at its issuing service, then update the private file. Never paste keys into chat, logs or committed files. This code does not automatically switch credentials after a rejection.

401 diagnostics include the endpoint host, credential source and recognized standard error codes such as `invalid_api_key` or `authentication_error`. They suppress the raw response message and unknown code/type fields because providers can echo the token there. A 407 is proxy authentication and is reported separately. Neither response triggers generation retries.

For an OpenAI-compatible endpoint ending in `/chat/completions` without query parameters, check the credential before launching a full population:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action auth -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\multipletreegp-dynamicLLMWarmStart.local.params
```

In IDEA use **EvoSpeakMain**, with program arguments `--check-auth -file <selected-params-file>`. This sends one authenticated **GET** to the same service's `/models` endpoint using the same proxy and headers as generation, with no prompt, inference request or GP run. The key is sent only in the provider's authentication header and is not printed. Unlike `--check-connection`, this check requires the configured credential. A successful model-list response does not prove access to a particular inference model or available billing quota. HTTP 403/404/405 can indicate restricted model-list permissions or an unsupported route, so those outcomes alone do not establish inference-token validity. Azure and Anthropic configurations are not supported by this model-list check.

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

The default generation prompt now follows the supplied **Insights Extraction -> New Heuristic Generation -> Expected Objective Effects** workflow. `warm-start-examples.json` contains the five input heuristics from that prompt, not the 100 individuals in the pasted model answer. Each example retains its reported scalar score as unverified historical metadata. Those scores are not comparable training observations without their original scenario, seeds and normalization, and are never loaded into GP fitness.

```properties
evospeak.examples-file = warm-start-examples.json
evospeak.max-examples = 10
evospeak.generation-language = English
```

The reference file can be a standalone ECJ population file or JSON with an `individuals` array. JSON individuals require `sequencing` and `routing` strings and may include a finite nonnegative `reportedFitness`. Use a small set of selected reference pairs, not a document combining a prompt, prose and a model answer. A missing/malformed reference file or too many examples stops before any generation request. An empty `evospeak.examples-file` disables references. Offline `evospeak.source=file` does not load or require examples.

References are parsed with the actual terminal/function grammar and bounded to depth 32 and 2047 nodes per tree. This allows source heuristics to be more complex than newly generated trees, which still obey `evospeak.max-tree-depth` and `evospeak.max-tree-nodes`. Reference parsing does not establish performance or simulation feasibility. Whole reference pairs are excluded from new LLM-generated populations; reusable substructures are encouraged.

Each batch prompt includes the five canonical pairs, per-tree terminal meanings/counts and structural warnings, the real scheduling scenario, normalized configured weights and the exact raw or benchmark-normalized score. The model must discuss context-dependent terms and cancellation rather than assume, for example, that additive `MWT` changes job ordering within one machine queue or that `TIS` directly encodes a due date. It must propose varied strategies, not just a long series of terminal substitutions. Exact duplicates/copies are checked in code; semantic novelty and explanation correctness still need scientific review.

To reproduce the attachment's raw `lambda1 * Fmean + lambda2 * WTmean`, where `lambda2 = 1 - lambda1`, explicitly select:

```properties
evospeak.objective-mode = multi
evospeak.objective.0 = mean-flowtime
evospeak.objective.1 = mean-weighted-tardiness
evospeak.weight.0 = 0.5
evospeak.weight.1 = 0.5
evospeak.normalization = false
```

The existing normalization default is unchanged. Different weights or single-objective settings are carried into the prompt, rather than overwritten by the attachment's illustrative 0.5/0.5 choice. `Fmean` and `WTmean` are identified as aliases for the corresponding configured objectives.

The LLM returns structured content rather than fabricating ECJ `Fitness` numbers:

```json
{
	"insights": [
		{"observation": "In reference 1, MWT is constant in one sequencing queue, so (- MWT W) favors larger W for finite values.", "referenceIds": [1]}
	],
	"individuals": [
		{
			"sequencing": "(/ PT W)",
			"routing": "(+ WIQ TRANT)",
			"explanation": {
				"sequencing": "Smaller PT/W favors short operations and higher positive job weights.",
				"routing": "Smaller WIQ+TRANT balances queued work with transport time.",
				"objectiveTradeOff": "These priorities may reduce flow time and protect important jobs, but their tardiness effect depends on due dates and congestion; improvement requires testing.",
				"referenceIds": [1]
			}
		}
	]
}
```

This one-pair example shows the schema, not a complete 100-individual response. Actual responses must contain exactly the requested batch count, 1-12 nonempty insights (up to 2000 characters each), and three nonempty explanation fields per pair (up to 4000 characters each). Reference IDs are zero-based, distinct and must exist in the supplied set; when references are disabled, use empty ID arrays. Insights and explanations are requested in the same content call, with no additional insight-only request.

Malformed batch structure/count/insights rejects the batch. A missing or malformed individual explanation rejects that pair. All failures are retained as corrective feedback for replacement batches, within the same bounded generation budget. Accepted rules and their explanations stay aligned after candidate rejection. The native ECJ population remains in the original `Number of Individuals`, `Individual Number`, `Evaluated: F`, `Fitness`, `Tree 0` and `Tree 1` style, serialized by ECJ itself.

Completed online generation also produces `warm-start-report.md` with bullet-point insights and the validated rule pairs plus sequencing, routing and expected-objective explanations. `reference-heuristics.json` snapshots the exact reference facts sent to the model; `generated-rules.json` preserves the explanations, task score and batch insights. Both generated population formats remain readable by the existing offline and independent analysis tools. These narrative fields are LLM interpretations, not measured performance or a proof of feasibility.

## Generation and Feasibility

```properties
evospeak.source = llm
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

At defaults, generation permits at most 40 content batches, each with at most 3 HTTP attempts. Cloud usage is billable; tune batch/count/token budgets before large runs. Optional `evospeak.prompt-file` supplies additional generation instructions. `evospeak.run-gp=false` generates and validates only.

Offline reuse accepts either the original ECJ-shaped text or `{"individuals":[{"sequencing":"PT","routing":"WIQ"}]}` JSON:

```properties
evospeak.source = file
evospeak.population-file = ../EvoSpeak/WarmStart/population_100_0.5_MO.txt
```

Old serialized fitness values are ignored. Accepted expressions are converted to correctly constrained GP individuals with fresh fitness and `Evaluated: F`. The new ECJ output can also be read by ECJ's native population loader. Offline inputs with insufficient distinct validated pairs fail rather than fabricate replacements; switch back to `source=llm` for automated generation.

Relative EvoSpeak/analysis file paths are resolved relative to the params file. Each run uses an ID-labelled `job.<seed>-<unique-suffix>` directory under `runs`. Statistics paths follow ECJ conventions; `GPMain` supplies an absolute path in its selected results directory.

## Independent Rule Analysis

Run `RuleAnalysisMain.main()` without training. With no population path, it chooses the newest generated population. To focus on a small selection:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action analyze -ExtraArgs @('--population','path\to\generated-population.txt','--indices','0,1','--prompt','Explain the congestion and urgency trade-off.','--output','path\to\report.md')
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

`status.json` stores the run ID, result paths, objective settings, configured weights, normalized weights, scenario, validation controls, seed, provider/model and completion state, without API keys. `generations.jsonl` stores the run ID, raw objectives, scalar fitness, normalization denominators, elapsed/cumulative time and the current best rule pair. An inactive objective that becomes non-finite after breeding is represented as JSON null rather than invalid JSON.

The default `runs` directory is Git-ignored because prompts, responses and experiment artifacts can be large or sensitive. Archive a run directory explicitly when needed for reproducibility. LLM generation is not guaranteed deterministic; reuse the saved population with the same GP seed for a controlled replay.

## Review of the Copied EvoSpeak

The original Java files compile when supplied explicitly, but the copied default configuration is not a portable online workflow:

- Source package declarations and params still point to `mengxu.algorithm.LLM.WarmStart`, while the copied directory is `EvoSpeak/WarmStart`.
- Population and utility paths reference `/Users/mengxu/...` on another machine.
- The example population uses fabricated, single-value fitness records although the MO configuration expects two objectives. V1 discards those fields and writes native ECJ serialization.
- The original MO state writes `weights[1]` unconditionally, and initializes weighted fitness only inside its normalization branch. Single-objective or normalization-disabled use is unsafe.
- `LLMWarmStart` is a text-processing stub; it does not implement a network generation/validation/retry workflow. The separate old `LLMcode` utility is not a configurable LLM API adapter.

The supplied `population_100_0.5_MO.txt` has 100 pairs that pass strict depth-8 syntax parsing in the current grammar. Syntax validity alone does not establish scheduling feasibility, diversity or performance. V1's offline mode applies the same simulation gate to these rules as to online-generated rules.

## Verification

The regression runner covers weighted/single-objective scoring, normalization-disabled operation, strict parsing, the original file format, native ECJ serialization round-trip, real bounded simulation validation, all four provider envelopes, authentication/missing-key/truncation failures, rejection feedback, GP blocking on insufficient valid individuals, automatic real GP runs, and independent report generation. Example-guided checks cover the supplied five references, unequal/single-objective prompt settings, raw versus normalized score descriptions, invalid citations, empty/missing insights and explanations, wrong batch counts, exact reference copying, malformed reference files, example-free generation and explanation alignment after retries. Launcher checks run two IDs through `GPMain`, verify `GPRun` fallback, isolate arguments/configuration and timing totals, honor explicit result paths, and reject existing result-file collisions.

Provider protocol behavior was tested with a local mock server, while GP and scheduling checks used the real Java implementation. Network regression checks exercise explicit HTTP proxies, JVM proxy selection, direct bypass, malformed proxy settings, categorized errors and the key-free HEAD entry point. Authentication regressions cover header formatting, known error-code filtering without leaking echoed credentials, no retry on 401, and the authenticated GET check's 200/401/403/404 outcomes without starting GP. Live diagnostics verified the proxy route with an unauthenticated HEAD request and subsequently confirmed `invalid_api_key` with one authenticated model-list GET. No real token was displayed or modified, and no live prompt or inference request was submitted during these checks. Successful inference authorization and model quality remain unverified. The offline smoke configuration completes three generations without a model. Full-size research experiments and claims of performance improvement are outside these smoke checks.