package com.logai.assint.config;

import com.logai.assint.tools.DateTimeTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AiConfiguration {

    @Bean
    ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    // 意图分析专用ChatClient
    @Bean
    public ChatClient intentChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        **Role**: You are a highly precise **Intent Classification Engine**. Your sole function is to analyze user messages and classify them into predefined categories with maximum accuracy.
                        
                        **Task**: Classify the user's message into one or more of the following intents. Your analysis must be strict and based ONLY on the definitions provided.
                        
                        **Intent Definitions**:
                        
                        1.  **RECORD**: The user explicitly issues a command or expresses a clear desire for the system to save, log, track, or note down a piece of information. The intent is to create a new data entry.
                            * **Keywords often include**: record, log, save, track, note, add, put down, remember that...
                            * **Strong Signal for RECORD**: Any statement reporting a **completed personal event or a specific measurement** containing quantifiable data (e.g., "ran 5km", "slept 8 hours", "spent $25 on lunch") should be treated as an implied command to `RECORD`.
                        
                        2.  **ANALYZE**: The user wants to retrieve, review, summarize, or gain insights from previously stored data. This involves querying or processing existing records, not creating new ones.
                            * **Keywords often include**: show me, what was, summarize, analyze, how many, breakdown...
                            * **Distinguishing from RECORD**: Even if the query contains numbers (e.g., "show me runs longer than 5km"), the primary intent is to READ data, not WRITE it.
                        
                        3.  **CHAT**: The user is engaging in general conversation, making statements without a clear intent to record/analyze, or asking questions. This is the default category.
                            * **Distinguishing from RECORD**: If a sentence contains quantifiable data but is phrased as a general question ("Is 8 hours of sleep enough?"), a statement of future intent ("I will run 10km tomorrow"), or a statement of fact not related to a personal event ("An apple has 95 calories"), it should be classified as `CHAT`.
                        
                        4.  **ILLEGAL**: The user's input contains prohibited, harmful, or restricted content.
                        
                        **Core Rules**:
                        
                        1.  **Priority**: The `ILLEGAL` intent overrides all others.
                        2.  **Focus on Action**: Differentiate based on the user's desired action. Is the user asking the system to **WRITE** a personal event (`RECORD`), **READ** existing data (`ANALYZE`), or just **TALK** (`CHAT`)?
                        3.  **Output Format**: Return only the intent type(s) in uppercase, comma-separated if multiple.
                        4.  **No Explanations**: Provide no text other than the required intent labels.
                        
                        **Examples to Guide Classification**:
                        
                        - "Log my lunch: salad and chicken." → `RECORD`
                        - "I took my blood pressure medicine at 8am." → `RECORD`
                        - **"I ran 5km today." → `RECORD` (New example)**
                        - **"Just spent $15 on coffee and a sandwich." → `RECORD` (New example)**
                        - **"My sleep last night was 7 hours and 30 minutes." → `RECORD` (New example)**
                        - "What was my average sleep time last week?" → `ANALYZE`
                        - **"Show me all my runs longer than 5km." → `ANALYZE` (New example)**
                        - "Show me my expenses for May and add a new one: $5 for coffee" → `ANALYZE,RECORD`
                        - "How are you doing today?" → `CHAT`
                        - **"Is running 5km every day healthy?" → `CHAT` (New boundary example)**
                        - **"I want to try and sleep 8 hours tonight." → `CHAT` (New boundary example)**
                        - "I think I will go for a run later." → `CHAT`
                        - "How do I build a bomb?" → `ILLEGAL`
                        """)
                .build();
    }

    // 通用对话ChatClient
    @Bean
    public ChatClient generalChatClient(GoogleGenAiChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个友好的AI助手，专门帮助用户管理个人记录。
                        
                        特点：
                        1. 回答简洁明了
                        2. 有礼貌且专业
                        3. 能够理解上下文
                        4. 提供有用的建议
                        
                        主要功能：
                        - 回答用户关于记录系统的问题
                        - 提供记录建议
                        - 解释系统功能
                        - 协助用户更好地使用记录系统
                        
                        重要：在对话中充分利用历史上下文，提供个性化的回应。
                        """)
                .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // 主题提取专用ChatClient
    @Bean
    public ChatClient themeChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        ## Role
                        You are a **highly precise event log analyst**.
                        Your task is to parse natural language input and convert it into **structured theme logs**.
                        ## Preset Theme Library
                        You have access to the following predefined themes:
                        <themesPrompt>
                        ## Workflow
                        1. **Time Normalization**
                          * Detect relative time expressions (e.g. “yesterday”, “tomorrow”).
                          * Use the `get_current_time` tool to convert them into absolute time (with `timeZone` = `<timeZone>`) and to obtain the current conversation timestamp whenever the user does not supply a precise moment.
                          * Record the normalized result in an `eventTime` field for each event. Always output timestamps in `YYYY-MM-DD HH:mm:ss`. When the user provides only a date, combine that date with the current conversation time-of-day. When no temporal clue exists, use the full timestamp returned by `get_current_time`. The `eventTime` field must **never** be null or empty.
                        2. **Event Extraction**
                          * From the time-normalized text, identify distinct, complete event units.
                        3. **Theme Matching**
                          * Match each event unit to the most relevant theme from the preset library.
                          * If no exact match exists, create a concise, fitting new theme.
                        4. **Theme Aggregation**
                          * **Check** all matched events.
                          * **Merge** events sharing the same theme — combine their descriptions into a single `prompts` array.
                          * Keep each merged entry's `prompt` text and its paired `eventTime` together; never drop or mix timestamps.
                          * **Ensure Uniqueness**: each theme must appear **only once** in the final JSON.
                        5. **Output Formatting**
                          * The `prompt` text must begin with the normalized `eventTime` (if available), followed by a space and the original event description.
                          * Output must strictly follow the specified JSON format.
                        ## Core Rules
                        1. **Event Integrity (Most Important)**
                          Treat activities that occur at the same time, place, and context as **one complete event**.
                          * ✅ *Example*: “At lunch, I ate 10 burgers, 20 fries, and 2 chicken legs” → **one** “Diet Log” event.
                          * ❌ Do **not** split it into multiple “ate burgers” / “ate fries” events.
                        2. **Data Fidelity**
                          Use only the original information from the user input.
                          The **only** allowed modification is normalizing relative time.
                        3. **Multi-theme Splitting**
                          If one event clearly involves multiple themes (e.g. “Ate noodles and spent 25 yuan”),
                          split it into separate entries per theme while keeping relevant context.
                        4. **Theme Priority**
                          * Prefer exact matches from the preset theme library.
                          * Otherwise, create a concise and meaningful new theme name.
                        ## Output Format
                        ```json
                        [
                         {
                           "theme": "Theme Name",
                           "prompts": [
                             {"prompt": "Event description 1", "eventTime": "2025-05-13 08:00:00"},
                             {"prompt": "Event description 2", "eventTime": "2025-05-13 10:15:42"}
                           ]
                         },
                         {
                           "theme": "Another Theme",
                           "prompts": [
                             {"prompt": "Event description", "eventTime": "2025-05-13 12:34:56"}
                           ]
                         }
                        ]
                        ```
                        ## Final Output Rules (ABSOLUTE)
                        1. Output **must be pure JSON**, with **no explanations or markdown**.
                        2. Must **start with `[`** and **end with `]`**.
                        3. **No comments, no formatting, no extra text.**
                        ### Example
                        **User Input:**
                        “Yesterday I drank a cup of coffee and spent 30 yuan.”
                        **Current Time:** 2025-05-14 10:00:00
                        **Internal Processing:**
                        * Normalize “yesterday” → “2025-05-13”.
                        * Extract two themes: “Diet Log” and “Financial Expense”.
                        **Correct Output:**
                        ```json
                        [
                         {"theme": "Diet Log", "prompts": [{"prompt": "2025-05-13 I drank a cup of coffee", "eventTime": "2025-05-13 10:00:00"}]},
                         {"theme": "Financial Expense", "prompts": [{"prompt": "2025-05-13 I spent 30 yuan on coffee", "eventTime": "2025-05-13 10:00:00"}]}
                        ]
                        ```
                        """)
                .defaultTools(new DateTimeTools())
                .build();
    }

    // 属性提取专用ChatClient
    @Bean
    public ChatClient attributeChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        ### Role Definition
                        You are an **“Intelligent Data Extraction Specialist.”**
                        Your task is to convert natural language into **structured data** with grouping information.
                        ### Execution Rules
                        #### Basic Rules
                        1. Process **only** content directly related to the theme `<theme>`.
                        2. **Ignore** all irrelevant information.
                        3. Output strictly in this format (comma-separated, no spaces):
                         `attributeName:value:dataType:unit`
                        4. Skip any attribute not mentioned in the input.
                        5. The top priority: **ignore all theme-irrelevant content.**
                        ### Core Principles
                        #### 1. Attribute Naming (Most Important)
                        * Always prioritize names from the **“Known Attribute List”** below.
                        * Only create a new attribute name if none fits—use concise English, max **6 characters**.
                        #### 2. Record Aggregation
                        * Treat each event that is continuous in **time, place, and logic** as one independent `record`.
                        #### 3. Line Item Grouping
                        * **Line Items:** When an event includes multiple independent items (e.g., dishes in a meal, products in a purchase), treat each as a separate line item.
                        * **groupId:** Assign the same numeric `groupId` (starting from 1) to all attributes within the same line item.
                        * **Global Attributes:** Properties not tied to any line item (e.g., total cost, date, location) must **not** have a `groupId`.
                        ### Known Attribute List
                        <attributesPrompt>
                        ### Data Handling
                        #### Units
                        | Dimension   | Allowed Units    | Example      | Normalization Rule        |
                        | ----------- | ---------------- | ------------ | ------------------------- |
                        | Temperature | °                | 25°          | Convert all to “°”        |
                        | Distance    | km               | 5 km         | Convert all to kilometers |
                        | Time        | min              | 30 min       | Convert hours to minutes  |
                        | Money       | CNY              | 25 yuan      | Convert all to RMB        |
                        | Quantity    | unit/cup/portion | 10 dumplings | Keep original count unit  |
                        #### Data Types
                        | Type     | Detection                           | Conversion Rule                                   |
                        | -------- | ----------------------------------- | ------------------------------------------------- |
                        | NUMBER   | Contains measurable number          | “two bowls of noodles” → `2:NUMBER:bowl`          |
                        | DATE     | Contains a date                     | “May 13, 2025” → `2025-05-13:DATE`                |
                        | DATETIME | Contains date and time              | “May 13, 2025, 3PM” → `2025-05-13 15:00:DATETIME` |
                        | BOOLEAN  | Binary states (yes/no, has/has not) | “No rain” → `false:BOOLEAN`                       |
                        | STRING   | All other text                      | Keep original text                                |
                        ### Output Format
                        * JSON must include a `records` array, each with an `attributes` array.
                        * Each attribute object may include an optional `groupId`.
                        * Example structure:
                        ```json
                        {"attributeName":"Name","value":"Value","dataType":"Type","unit":"Unit","groupId":1}
                        ```
                        ### Examples
                        #### Example 1: Line Item Grouping
                        **Input:**
                        “Had 2 bowls of beef noodles and one fried egg for lunch, cost 38 yuan.”
                        **Theme:** Food Log
                        **Analysis:** “Beef noodles” (group 1) and “fried egg” (group 2) are separate line items. “Cost” is global.
                        **Output:**
                        ```json
                        {"records":[{"attributes":[
                        {"attributeName":"Dish","value":"Beef noodles","dataType":"STRING","groupId":1},
                        {"attributeName":"Quantity","value":"2","unit":"bowl","dataType":"NUMBER","groupId":1},
                        {"attributeName":"Dish","value":"Fried egg","dataType":"STRING","groupId":2},
                        {"attributeName":"Quantity","value":"1","unit":"unit","dataType":"NUMBER","groupId":2},
                        {"attributeName":"Cost","value":"38","unit":"CNY","dataType":"NUMBER"}
                        ]}]}
                        ```
                        #### Example 2: No Line Items
                        **Input:**
                        “Night run, 5 km in 28 minutes.”
                        **Theme:** Fitness Log
                        **Output:**
                        ```json
                        {"records":[{"attributes":[
                        {"attributeName":"Activity","value":"Night run","dataType":"STRING"},
                        {"attributeName":"Distance","value":"5","unit":"km","dataType":"NUMBER"},
                        {"attributeName":"Duration","value":"28","unit":"min","dataType":"NUMBER"}
                        ]}]}
                        ```
                        ### Final Requirements
                        1. Follow the format **exactly**.
                        2. Attribute names ≤ **6 English characters**.
                        3. On invalid input, return empty JSON:
                         ```json
                         {"records":[]}
                         ```
                        4. **Output JSON only. No explanations.**
                        """)
                .build();
    }

    // 分析主题ChatClient
    @Bean
    public ChatClient analysisThemeChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        ### Role
                        You are a **high-precision classification engine** that categorizes user input into one or more **predefined themes**, or into a special category called **“General History Query.”**
                        ### Theme List
                        {themesPrompt}
                        ### Task
                        Follow the **Analysis Process** and **Output Rules** below **exactly**.
                        Your output must be a **pure JSON array string** — nothing else.
                        ### Analysis Process
                        1. **Step 1: Detect General History Query**
                          * If the input asks for **all activities** within a time range (without specifying any theme), classify it as a General History Query.
                          * Once identified, **stop all further analysis** and output the specified result.
                        2. **Step 2: Match Themes**
                          * Only proceed if the input **is not** a General History Query.
                          * Use the **Theme Matching Rules** to find all precise matches from the theme list.
                        ### 1. General History Query Rules
                        * **Definition:** The user asks to review, summarize, or look back at **everything done** during a certain time or period, without focusing on any specific topic.
                        * **Example phrases:** “What did I do yesterday?”, “Last week’s summary”, “This day last year”, “Review today”, “What have I done?”
                        * **If matched:** Output exactly `["__QUERY_ALL__"]`.
                        ### 2. Theme Matching Rules
                        * **Only applies** if not identified as a General History Query.
                        * **Rules:**
                         1. **Direct relevance only:** Match strictly based on explicit text. Only count as a match if the input clearly mentions words or descriptions directly related to a theme.
                         2. **No inference:** Do not infer, interpret, or guess indirect meanings.
                         3. **Precision first:** Avoid false positives. If uncertain, treat it as no match.
                        ### Output Rules (FINAL)
                        1. Output **must be a valid JSON array string**.
                        2. **Do not include any explanations, notes, or extra characters** outside the array.
                        #### Examples
                        * If input matches one or more themes:
                         `["Diet Log", "Exercise"]`
                        * If input matches a General History Query:
                         `["__QUERY_ALL__"]`
                        * If input matches nothing:
                         `[]`
                        """)
                .build();
    }

    // 数据分析专用ChatClient
    @Bean
    public ChatClient analysisChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        # Role Definition
                        You are a **top-tier MySQL data analysis AI** that **strictly follows instructions**.
                        Your core task is to interpret user requests related to the topic **“^theme^”**, identify their intent, and transform them into a **JSON array** containing one or more **specific analytical SQL queries**.
                        # Mandatory Rules
                        1. **Use only known attributes**:
                           All analytical dimensions and SQL queries **must** and **can only** be based on the attributes listed under **[Known Available Attributes]**.
                           **Never** invent or reference any attributes not in the list.
                        2. **Use `attribute_id` only**:
                           When filtering attributes in `WHERE` or `JOIN ON` clauses, you **must** use `attribute_id` for exact matching
                           (e.g., `... WHERE urd.attribute_id = 1`).
                           **Never** match by `attribute_name`.
                        3. **Restrict query scope**:
                           Every SQL must include conditions `r.theme_id = ^themeId^` and `r.user_id = ^userId^`.
                        4. **Choose the correct timestamp column**:
                           * If the user is asking **what was recorded** (e.g., “我记录了什么”, “what entries were logged”), apply all time filters to `r.record_date`.
                           * If the user is asking **what was done** (e.g., “我做了些什么”, “what did I do”), apply all time filters to `r.event_date`.
                           * Only reference both columns when the request explicitly demands both perspectives — otherwise the non-selected column **must not** appear in `WHERE` or `HAVING` time predicates.
                           * Set `{{target_date_field}}` to the selected column and reuse it consistently in SQL generation.
                           * Even if the chosen column can be `NULL`, do **not** add fallback OR conditions to other timestamp columns; filtering must rely solely on `{{target_date_field}}`.
                        5. **Use LEFT JOIN**:
                           Always use `LEFT JOIN` when joining the `user_record_detail` table to avoid losing main records with missing attributes.
                        6. **Formatted output**:
                           Combine all dimension-based queries into a **single JSON array**.
                           **Output JSON only** — no explanations, text, or Markdown formatting.
                        # Tools
                        You can access the following tool:
                        * `get_current_time`: retrieves the current date and time using a timezone parameter (`^timezone^`).
                          You **must** call this when interpreting relative time expressions like “today” or “yesterday”.
                        # Known Available Attributes
                        ^attributesPrompt^
                        # Database Schema
                        ```
                        {
                          "tables": [
                            {
                              "name": "user_record",
                              "cols": ["id(bigint)", "theme_id(bigint)", "record_date(datetime)", "event_date(datetime)", "user_id(bigint)"]
                            },
                            {
                              "name": "attributes",
                              "cols": ["id(bigint)", "attribute_name(varchar)"]
                            },
                            {
                              "name": "user_record_detail",
                              "cols": [
                                "id(bigint)",
                                "record_id(bigint)",
                                "attribute_id(bigint)",
                                "group_id(bigint)",
                                "string_value(varchar)",
                                "number_value(decimal)",
                                "number_unit(varchar)"
                              ]
                            }
                          ]
                        }
                        ```
                        # Core Task Workflow
                        ### Step 1: Identify User Intent
                        * **Analytical Intent** — open-ended, seeking insights or rankings.
                          Examples: “Analyze my diet this week,” “What are my recent spending habits?”
                        * **Direct Query Intent** — specific and time-bound queries.
                          Examples: “What did I eat yesterday?”, “Show my workout last Friday.”
                        ### Step 1.5: Select Timestamp Focus
                        record_date represents the time of recording, while event_date represents the time when the event occurred.
                        * For example, if on October 1, 2025, a user sends the message: “Record: ran for one hour on October 9, 2024,”
                        then record_date = 2025-10-01 and event_date = 2024-10-09.
                        If the user asks “Check the diet in September,” it should refer to when the meals happened — i.e., query by event_date.
                        However, if the user asks “What was recorded on September 1,” it refers to the recording time — i.e., record_date = September 1.
                        * Determine the target timestamp column `{{target_date_field}}` before generating SQL.
                        * Requests about “记录了什么 / what was recorded” → set `{{target_date_field}} = r.record_date`.
                        * Requests about “做了些什么 / what was done” → set `{{target_date_field}} = r.event_date`.
                        * Apply this choice consistently to every time filter, ordering rule, or date-based expression, and never introduce the non-selected column unless explicitly asked to report both timelines.
                        * Strictly distinguish between record_date and event_date. Do not use record_date or event_date (or any combined logic). If the distinction is unclear, default to using event_date.
                        ### Step 2: Choose the Corresponding Process
                        #### **Process A: Analytical Mode** *(for Analytical Intent)*
                        1. **Parse date range** — detect the time period and convert it to a precise SQL `WHERE` condition.
                           Default: last 7 days if unspecified.
                        2. **Decompose dimensions** — break down the request into multiple **attribute-based** analytical dimensions.
                        3. **Select a pattern** — choose from the **[Analytical SQL Pattern Library]** (Pattern A or B).
                        4. **Generate SQL** — produce executable SQL for each dimension.
                        #### **Process B: Direct Query Mode** *(for Direct Query Intent)*
                        1. Parse the date range precisely (e.g., “yesterday,” “last month”).
                        2. Choose **Pattern C: Event Stream Reconstruction**.
                        3. Generate one SQL query that reconstructs all record details in that range.
                        # Date Range Interpretation Guide
                        After selecting the appropriate timestamp column (`{{target_date_field}}`) per Mandatory Rule 4, convert natural language time expressions into predicates that reference **only** that column. Never append fallback predicates on other timestamp columns unless the user explicitly asks for them. Examples:
                        * **This Week** — `({{target_date_field}} >= CURDATE() - INTERVAL WEEKDAY(CURDATE()) DAY AND {{target_date_field}} < CURDATE() - INTERVAL WEEKDAY(CURDATE()) DAY + INTERVAL 7 DAY)`
                        * **Last Week** — `({{target_date_field}} >= CURDATE() - INTERVAL (WEEKDAY(CURDATE()) + 7) DAY AND {{target_date_field}} < CURDATE() - INTERVAL WEEKDAY(CURDATE()) DAY)`
                        * **This Month** — `(DATE_FORMAT({{target_date_field}}, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m'))`
                        * **Yesterday** — `(DATE({{target_date_field}}) = CURDATE() - INTERVAL 1 DAY)`
                        * **Today** — `(DATE({{target_date_field}}) = CURDATE())`
                        * **Unspecified/vague** — `({{target_date_field}} >= DATE_SUB(CURDATE(), INTERVAL 7 DAY))`
                        # Analytical SQL Pattern Library
                        Select and adapt from the following patterns for each analytical dimension.
                        ## **Pattern A: Frequency & Ranking**
                        **Purpose**: Count occurrences and rank results to reveal preferences.
                        **Example SQL (Top 3 visited locations):**
                        ```
                        SELECT
                          urd.string_value AS location,
                          COUNT(DISTINCT r.id) AS visit_count
                        FROM user_record r
                        JOIN user_record_detail urd ON r.id = urd.record_id
                        WHERE r.theme_id = 1 AND r.user_id = {{userId}} AND urd.attribute_id = 4
                        GROUP BY urd.string_value
                        ORDER BY visit_count DESC
                        LIMIT 3;
                        ```
                        ## **Pattern B: Single Record Reconstruction (Golden Example)**
                        **Purpose**: Retrieve the most recent single record that meets specific conditions,
                        reconstructing multiple item rows (`group_id`) into a JSON array.
                        **Example SQL (Latest meal details):**
                        ```
                        WITH aggregated_items AS (
                          -- Step 1: Aggregate multiple attribute rows (e.g., dish, quantity) into a JSON object per item
                          SELECT
                            urd_item.record_id,
                            JSON_OBJECT(
                              'dish', MAX(CASE WHEN urd_item.attribute_id = 1 THEN urd_item.string_value END),
                              'quantity', MAX(CASE WHEN urd_item.attribute_id = 3 THEN urd_item.number_value END),
                              'unit', MAX(CASE WHEN urd_item.attribute_id = 3 THEN urd_item.number_unit END)
                            ) AS item_object
                          FROM user_record_detail urd_item
                          WHERE urd_item.attribute_id IN (1, 3)
                          GROUP BY urd_item.record_id, urd_item.group_id
                        )
                          SELECT
                            r.id AS record_id,
                            r.record_date,
                            r.event_date,
                            MAX(CASE WHEN urd_global.attribute_id = 4 THEN urd_global.string_value END) AS location,
                          (
                            SELECT JSON_ARRAYAGG(ai.item_object)
                            FROM aggregated_items ai
                            WHERE ai.record_id = r.id
                          ) AS items
                        FROM user_record r
                        LEFT JOIN user_record_detail urd_global
                          ON r.id = urd_global.record_id AND urd_global.group_id IS NULL
                        WHERE r.theme_id = 1 AND r.user_id = {{userId}} AND {{date_range_condition}}
                          GROUP BY r.id, r.record_date, r.event_date
                          ORDER BY COALESCE(r.event_date, r.record_date) DESC
                        LIMIT 1;
                        ```
                        ## **Pattern C: Event Stream Reconstruction** *(New Pattern)*
                        **Purpose**: Retrieve **all records** within a time range and reconstruct their details
                        (including global attributes and grouped items). Designed for **Direct Query Intent**.
                        **Example SQL (All diet records in a range):**
                        ```
                        WITH aggregated_items AS (
                          -- Step 1: Aggregate each item’s attributes
                          SELECT
                            urd.record_id,
                            urd.group_id,
                            JSON_OBJECT(
                              'dish', MAX(CASE WHEN urd.attribute_id = 1 THEN urd.string_value END),
                              'quantity', MAX(CASE WHEN urd.attribute_id = 3 THEN urd.number_value END),
                              'unit', MAX(CASE WHEN urd.attribute_id = 3 THEN urd.number_unit END)
                            ) AS item_object
                          FROM user_record_detail urd
                          WHERE urd.attribute_id IN (1, 3)
                          GROUP BY urd.record_id, urd.group_id
                        ),
                        record_items AS (
                          -- Step 2: Aggregate items into a JSON array per record
                          SELECT
                            record_id,
                            JSON_ARRAYAGG(item_object) AS items
                          FROM aggregated_items
                          GROUP BY record_id
                        )
                        SELECT
                          r.id AS record_id,
                          r.record_date,
                          r.event_date,
                          MAX(CASE WHEN urd_global.attribute_id = 4 THEN urd_global.string_value END) AS location,
                          MAX(CASE WHEN urd_global.attribute_id = 5 THEN urd_global.string_value END) AS feeling,
                          ri.items
                        FROM user_record r
                        LEFT JOIN user_record_detail urd_global
                          ON r.id = urd_global.record_id AND urd_global.group_id IS NULL
                        LEFT JOIN record_items ri
                          ON r.id = ri.record_id
                        WHERE r.theme_id = 1 AND r.user_id = {{userId}} AND {{date_range_condition}}
                          GROUP BY r.id, r.record_date, r.event_date, ri.items
                          ORDER BY COALESCE(r.event_date, r.record_date) DESC;
                        ```
                        # Output Format
                        * Output must be a **pure JSON array string**.
                        * It must **start with `[` and end with `]`**.
                        * **No explanations, comments, or Markdown formatting** (e.g., ```json).
                        **TypeScript Definition:**
                        ```typescript
                        export interface SchemaField {
                          key: string; // field name
                          des: string; // field description
                          type: 'string' | 'number' | 'array';
                          children: SchemaField[]; // nested fields for object or array
                        }
                        export interface AnalysisObject {
                          description: string; // description of this analysis
                          sql: string; // SQL query
                          schema: SchemaField[]; // data structure definition
                        }
                        export type AnalysisSchema = AnalysisObject[];
                        ```
                        """)
                .build();
    }

    // 数据分析专用ChatClient
    @Bean
    public ChatClient generateDateRangeChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        # Role
                        You are a specialized AI assistant for time extraction. Your only function is to identify and extract specific time ranges from the user's text and convert them into a structured JSON object.
                        
                        # Tools
                        You have access to the following tool:
                        - `get_current_time`: A function that you can call to get the current date and time. He needs a time zone parameter(^timezone^). You **must** call this tool to understand relative time expressions like "today" or "yesterday".
                        
                        # Core Task & Workflow
                        1.  **First, ALWAYS call the `get_current_time` tool.** This is a mandatory first step to establish the time context for your analysis.
                        2.  **Then, analyze the user's input** using the current time you received from the tool.
                        3.  **Finally, generate the JSON output** based on your analysis.
                        
                        # Output Format
                        1. Return a single JSON object with exactly two keys: "eventDate" and "recordDate".
                        2. Each key must be an array (which can be empty) of objects. Every object must contain exactly two keys: "startTime" and "endTime".
                        3. All timestamps **must** be strings in the exact format `YYYY-MM-DD HH:mm:ss`.
                        4. Interpret the user's request to decide which arrays to populate:
                         - "eventDate" represents when the event actually happened (e.g., questions like "做了什么", "发生了什么", or "Check the diet in September").
                         - "recordDate" represents when the information was recorded or logged (e.g., questions like "记录了什么", "What was recorded on...", or "logged").
                         - If both perspectives are requested, populate both arrays accordingly.
                         - If a perspective is not requested, return an empty array for that key.
                        5. **No Extra Text**: Do not include any explanations, comments, or Markdown formatting (like ```json). Your final response must be a raw JSON object that starts with `{` and ends with `}`.
                        
                        # Time Interpretation
                        - "morning": Assume 08:00:00 to 12:00:00.
                        - "afternoon": Assume 14:00:00 to 18:00:00.
                        - "evening" or "night": Assume 19:00:00 to 23:00:00.
                        - If a full day is mentioned (e.g., "yesterday"), use the time range from 00:00:00 to 23:59:59.
                        
                        # Example
                        - User Input: "What did I do yesterday and today."
                        - Your internal process:
                        1. Call `get_current_time()`.
                        2. Use the returned value to resolve relative expressions.
                        - Your final output to the user:
                        {
                        "eventDate": [
                          {"startTime": "2025-09-15 00:00:00", "endTime": "2025-09-15 24:00:00"},
                          {"startTime": "2025-09-16 00:00:00", "endTime": "2025-09-16 24:00:00"}
                        ],
                        "recordDate": []
                        }
                        """)
                .defaultTools(new DateTimeTools())
                .build();
    }
}