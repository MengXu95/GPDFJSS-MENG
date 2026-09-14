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

The pre-launch task compiles the required source and reflection-loaded ECJ classes. In IntelliJ, run `EvoSpeakMain.main()` with the repository root as the working directory and `libraries/*.jar` on the classpath. Running `GPRun` directly is not the V1 entry point because it bypasses generation configuration.

From the repository root:

```powershell
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train -ParamsFile .\src\mengxu\algorithm\EvoSpeakV1\smoke.params
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action train
.\src\mengxu\algorithm\EvoSpeakV1\run.ps1 -Action test
```

The first command checks the local environment without a model. The second needs the LLM configuration below. The regression tests use a local mock HTTP server and real scheduling simulations; they do not consume cloud tokens.

## LLM Configuration

All provider URLs are full endpoints, not just a host. Configure a model supported by your account or deployment. API keys are read from the environment variable named by `llm.api-key-env`; never put keys in source, params, URLs or Git. After changing persistent Windows environment variables, restart VS Code/IntelliJ so its launched JVM inherits them.

| Provider | `llm.provider` | Endpoint | Authentication |
|---|---|---|---|
| OpenAI or compatible service | `openai-compatible` | Default `https://api.openai.com/v1/chat/completions`; override for another service | Bearer token from the named environment variable |
| Azure OpenAI | `azure-openai` | Full `https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions?api-version=<supported-version>` | `api-key` header; e.g. `llm.api-key-env=AZURE_OPENAI_API_KEY` |
| Anthropic | `anthropic` | Default `https://api.anthropic.com/v1/messages` | `x-api-key`; set `llm.api-key-env=ANTHROPIC_API_KEY` |
| Local Ollama | `ollama` | Default `http://localhost:11434/api/chat` | No key required; start Ollama and install the model first |

Example for an OpenAI-compatible API:

```properties
llm.provider = openai-compatible
llm.model = gpt-4.1-mini
llm.api-key-env = OPENAI_API_KEY
llm.timeout-seconds = 120
llm.max-output-tokens = 8000
llm.max-attempts = 3
```

For providers requiring `max_completion_tokens`, set `llm.token-parameter=max_completion_tokens`. Temperature is omitted unless `llm.temperature` is explicitly set, because some models do not support it. The adapter supports text chat completions/messages, not every provider's Responses API or streaming API.

Only HTTPS endpoints are accepted except loopback HTTP for local services. Redirects are not followed. Transient transport/429/5xx failures have a bounded retry count; authentication and other client errors fail immediately. Truncated model responses are rejected with instructions to reduce batch size or increase output tokens.

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

Relative EvoSpeak/analysis file paths are resolved relative to the params file. Every run uses a new directory under `runs`, so seeds or methods do not overwrite each other.

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

Each completed run directory contains `status.json`, `validation-report.json`, `generated-population.txt` and `generated-rules.json`. Online runs save generation prompts/responses and a `reference-heuristics.json` snapshot. Completed online generation also saves `warm-start-report.md`; generation insights remain in the batch records of both the validation report and enriched rules JSON. Failed validation does not publish a complete population or warm-start report. GP runs add `evolution.out.stat`, `generations.jsonl`, `final-population.txt` and `best-rules.txt` (best in the final generation). The statistics log separately contains ECJ's best-of-run record.

`status.json` stores the objective settings, configured weights, normalized weights, scenario, validation controls, seed, provider/model and completion state, without API keys. `generations.jsonl` stores raw objectives, scalar fitness, normalization denominators, elapsed time and the current best rule pair. An inactive objective that becomes non-finite after breeding is represented as JSON null rather than invalid JSON.

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

The regression runner covers weighted/single-objective scoring, normalization-disabled operation, strict parsing, the original file format, native ECJ serialization round-trip, real bounded simulation validation, all four provider envelopes, authentication/missing-key/truncation failures, rejection feedback, GP blocking on insufficient valid individuals, automatic real GP runs, and independent report generation. Example-guided checks cover the supplied five references, unequal/single-objective prompt settings, raw versus normalized score descriptions, invalid citations, empty/missing insights and explanations, wrong batch counts, exact reference copying, malformed reference files, example-free generation and explanation alignment after retries.

No cloud API keys were available in the development environment. HTTP provider behavior was tested with a local mock server, while GP and scheduling checks used the real Java implementation. Live account/deployment access and model quality must be verified after configuring credentials. The offline smoke configuration completes three generations without a model. Full-size research experiments and claims of performance improvement are outside these smoke checks.