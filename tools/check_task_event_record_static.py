"""Read-only source consistency checks. Does not build, execute SQL, or run the app."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/io/github/peanutpersimmon/rilo"
def read(path):
    return (SRC / path).read_text(encoding="utf-8")
def require(condition, message):
    if not condition:
        raise AssertionError(message)

entity = read("data/local/room/entity/SessionEntity.kt")
migration = read("data/local/room/SessionEventSourceMigration.kt")
database = read("data/local/room/RidoDatabase.kt")
require("version = 3" in database, "Database version")
require("addMigrations(TagHierarchyMigration, SessionEventSourceMigration)" in database, "Migration registration")
require("Migration(1, 2)" in read("data/local/room/TagHierarchyMigration.kt"), "Original migration")
require("Migration(2, 3)" in migration, "New migration")
require('sourceEventId INTEGER REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL' in migration, "Migration FK")
require('index_sessions_sourceEventId ON sessions(sourceEventId)' in migration, "Migration index")
require('childColumns = ["sourceEventId"], onDelete = ForeignKey.SET_NULL' in entity, "Entity FK")
require('Index("sourceEventId")' in entity and 'val sourceEventId: Long? = null' in entity, "Entity nullable origin")
require("CASCADE" not in migration and "UPDATE sessions" not in migration, "No cascade or inferred backfill")
require(migration.count("db.execSQL") == 2, "Only two schema statements")
mapper = read("data/mapper/RoomMappers.kt")
require(mapper.count("sourceEventId = sourceEventId") == 2, "Both mapper directions")
require("val sourceEventId: Long? = null" in read("domain/model/DomainModels.kt"), "Domain origin")
inputs = read("domain/usecase/SessionUseCases.kt")
require("val sourceEventId: Long? = null" in inputs and "sourceEventId = sourceEventId" in inputs, "Create input")
editor = read("presentation/editor/session/SessionEditorViewModel.kt")
require(editor.count("sourceEventId = state.sourceEventId") == 2, "Edit and create save origin")
require("sourceEventId = e.id" in editor and "getEvent?.invoke(eventId)" in editor, "Saved Event prefill")
require("baseEnd?.takeIf" in editor, "Preserve exact actual end on metadata edit")
operations = read("data/repository/RoomSessionOperations.kt")
require(operations.count("validate(session)?") == 5, "All record save/create/start/edit/restore validation")
require("session.startAtMillis > now" in operations and "session.endAtMillis?.let { it > now }" in operations, "Future start and end")
require("endAtMillis > nowMillis()" in operations, "Future stop")
require("sessionDao.getById(session.id)?.sourceEventId" in operations, "No stale origin reattachment")
require("countExplicitActualOverlaps" not in operations, "Legacy Event cannot block actual record")
projector = read("domain/service/TimelineProjector.kt")
require("resolve(sessions, emptyList()," in projector, "Display resolver receives no Events")
require("EVENT_IMPLICIT" not in projector and "EVENT_EXPLICIT" not in projector, "No Event actual branch")
policy = read("domain/service/TaskTemporalPolicy.kt")
require("it.taskId == task.id && it.endAtMillis == at" in policy, "Exact relational dedup")
require("plusDays(1).atStartOfDay(zone)" in policy, "Date-only deadline boundary")
endpoint = read("presentation/today/TimelineEndpointMapper.kt")
require("actualStartAtMillis" not in endpoint and "actualEndAtMillis" not in endpoint, "No legacy Event facts in Today mapper")
require("task.plannedDateEpochDay" in policy and "task.createdAtMillis <= reference" in policy, "Historical state")
require("TASK_COMPLETION_POINT" in endpoint, "Completion endpoint")
for path in ("ui/feature/today/TodayPlanSummary.kt", "ui/feature/today/TimelineEntryContent.kt"):
    require("LineThrough" in read(path), "Title completion style " + path)
require("onItemClick(item)" in read("ui/feature/today/TodayPlanSummary.kt"), "Summary direct edit")
require("onEventRecord" in read("ui/feature/today/TodayEditorSheet.kt"), "Inline Event to Record")
require("onCreateRecord" in read("ui/RidoApp.kt"), "Root Event to Record")
require("TaskTemporalPolicy.completionPoints" in read("ui/feature/plan/PlanTimeline.kt"), "History uses shared dedup")
require("stopAndComplete =" in read("MainActivity.kt"), "Timer dependency wired")
# Lexical delimiter check, including nested interpolation expressions. This is not a Kotlin compiler.
def check_delimiters(text, name):
    def code(i, expected=None):
        stack=[]
        while i < len(text):
            if text.startswith("//", i):
                end=text.find("\n", i); i=len(text) if end < 0 else end+1; continue
            if text.startswith("/*", i):
                depth=1; i+=2
                while i < len(text) and depth:
                    if text.startswith("/*", i): depth+=1; i+=2
                    elif text.startswith("*/", i): depth-=1; i+=2
                    else: i+=1
                require(depth == 0, name + ": unclosed comment"); continue
            c=text[i]
            if c in ('"', "'"):
                raw=text.startswith('"""', i)
                quote='"""' if raw else c
                i+=len(quote)
                while i < len(text):
                    if text.startswith(quote, i): i+=len(quote); break
                    if not raw and text[i] == "\\": i+=2; continue
                    if c == '"' and text.startswith("${", i): i=code(i+2, "}"); continue
                    i+=1
                else: raise AssertionError(name + ": unclosed string")
                continue
            if c in "({[": stack.append({"(":")", "{":"}", "[":"]"}[c])
            elif c in ")}]":
                if not stack and c == expected: return i+1
                require(bool(stack) and stack.pop() == c, name + ": mismatched delimiter at " + str(i))
            i+=1
        require(not stack and expected is None, name + ": unclosed delimiter")
        return i
    code(0)
files=list((ROOT / "app/src/main/java").rglob("*.kt")) + list((ROOT / "app/src/test/java").rglob("*.kt"))
for file in files:
    text=file.read_text(encoding="utf-8")
    require(not re.search(r"^(<<<<<<<|=======|>>>>>>>)", text, re.M), str(file) + ": conflict marker")
    check_delimiters(text, str(file.relative_to(ROOT)))
print(f"PASS: schema/field/routing/temporal source assertions; delimiters and conflict markers in {len(files)} Kotlin files.")
print("Static source checks only; no Gradle, Git, SQL execution, Android runtime, or test suite execution.")
