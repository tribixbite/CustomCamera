# Documentation Navigation Guide

## Quick Start
New to the project? Start here:
1. Read `CLAUDE.md` - Project overview and quick reference
2. Read `docs/ARCHITECTURE.md` - System design and structure
3. Check `memory/todo.md` - Current tasks and priorities

## Documentation Structure

### Project Root Files
- **CLAUDE.md** - Primary configuration and quick reference guide
  - Build commands
  - Development workflow
  - Architecture overview
  - Feature summary
  - Spec-driven development guide

- **README.md** - Public-facing project description (if exists)
- **CONFERENCE_DEMO_GUIDE.md** - Presentation guide for demos
- **PHASE8_SUMMARY.md** - Provider Pattern refactoring summary
- **VIDEO_STABILIZATION_GUIDE.md** - Video stabilization implementation

### Documentation Directory (`docs/`)

#### Core Documentation
- **docs/ARCHITECTURE.md** - Complete system architecture
  - Directory structure
  - Component breakdown
  - Data flows
  - Plugin system design
  - Testing architecture
  - Build system

- **docs/SESSION_HISTORY.md** - Historical session logs
  - Completed work details
  - Implementation notes
  - Bug fixes
  - Feature additions

- **docs/NAVIGATION.md** - This file
  - Documentation index
  - Content location guide

#### Specifications (`docs/specs/`)
- **docs/specs/SPEC_TEMPLATE.md** - Template for new feature specs
- **docs/specs/[feature-name].md** - Individual feature specifications

### Memory Directory (`memory/`)
**Purpose**: Working memory, active development tracking

- **memory/todo.md** - **PRIMARY TASK LIST**
  - Current priorities (P0/P1/P2/P3)
  - Active issues
  - Next session tasks
  - Technical debt tracking

- **memory/PROVIDER_PATTERN_REFACTORING.md** - Refactoring plan
- **memory/UX_IMPROVEMENTS.md** - UX enhancement guide
- **memory/PIP.md** - PiP implementation details

### Test Documentation (`app/src/test/`)
- **app/src/test/README_TESTS.md** - Complete testing guide
  - Test framework usage
  - Running tests
  - Writing new tests
  - Performance testing

## Finding Information

### "I want to..."

#### Build and Deploy
→ `CLAUDE.md` - "Build Commands" section
→ `.github/workflows/ci.yml` - CI/CD pipeline

#### Understand the Architecture
→ `docs/ARCHITECTURE.md` - Complete system design
→ `CLAUDE.md` - "Architecture Quick Reference" section

#### Find What to Work On
→ `memory/todo.md` - **START HERE**
→ `CLAUDE.md` - "Current Status" section

#### Add a New Feature
1. Create spec: `docs/specs/[feature-name].md`
2. Update architecture: `docs/ARCHITECTURE.md`
3. Add tasks: `memory/todo.md`
4. Follow: `CLAUDE.md` - "Spec-Driven Development" section

#### Add a New Plugin
→ `docs/ARCHITECTURE.md` - "Plugin System Architecture" section
→ `CLAUDE.md` - "Plugin Development Pattern" section

#### Understand Past Work
→ `docs/SESSION_HISTORY.md` - Historical sessions
→ `git log` - Commit history
→ GitHub Releases - Release notes

#### Debug an Issue
→ `CLAUDE.md` - "Debugging Reference" section
→ `memory/todo.md` - Known issues
→ `adb logcat` - Runtime logs

#### Run Tests
→ `app/src/test/README_TESTS.md` - Testing guide
→ `CLAUDE.md` - "Test Commands" section

#### Update Settings
→ `docs/ARCHITECTURE.md` - "StateFlow Reactive Architecture" section
→ `CLAUDE.md` - "Settings Addition Pattern" section

## Content Organization Rules

### What Goes Where

#### CLAUDE.md
**Purpose**: Primary development reference, quick lookups
**Content**:
- Build commands
- Development workflow
- Quick architecture reference
- Feature summary
- Debugging quick reference
- Spec-driven development guide
- Quality standards

**NOT for**:
- Detailed session logs (→ docs/SESSION_HISTORY.md)
- Complete architecture details (→ docs/ARCHITECTURE.md)
- Active tasks (→ memory/todo.md)

#### docs/ARCHITECTURE.md
**Purpose**: Complete system design reference
**Content**:
- Full directory structure
- Component breakdowns
- Data flow diagrams
- Plugin system details
- Testing infrastructure
- Build system details
- Manager/service catalogs

**NOT for**:
- Implementation history (→ docs/SESSION_HISTORY.md)
- Active development tasks (→ memory/todo.md)

#### docs/SESSION_HISTORY.md
**Purpose**: Historical record of completed work
**Content**:
- Session summaries
- Implementation details
- Bug fixes
- Feature additions
- Commits and versions

**NOT for**:
- Future plans (→ memory/todo.md, docs/specs/)
- Current architecture (→ docs/ARCHITECTURE.md)

#### memory/todo.md
**Purpose**: Active task tracking, current priorities
**Content**:
- Current tasks (P0/P1/P2/P3)
- Active issues
- Next session priorities
- Technical debt

**NOT for**:
- Completed work (→ docs/SESSION_HISTORY.md)
- Architecture details (→ docs/ARCHITECTURE.md)
- Permanent documentation (→ docs/)

#### docs/specs/
**Purpose**: Feature specifications and requirements
**Content**:
- Feature requirements
- Technical design
- Implementation plans
- Testing strategy
- Success metrics

**NOT for**:
- Implementation history (→ docs/SESSION_HISTORY.md)
- General architecture (→ docs/ARCHITECTURE.md)

## Update Frequency

### Every Session
- `memory/todo.md` - Mark progress, add new tasks

### When Completing Major Work
- `docs/SESSION_HISTORY.md` - Add session summary
- `git commit` with descriptive message
- `memory/todo.md` - Mark tasks complete

### When Adding Features
- `docs/specs/[feature].md` - Create spec
- `docs/ARCHITECTURE.md` - Update if architecture changes
- `memory/todo.md` - Add implementation tasks

### Rarely (Only Major Changes)
- `CLAUDE.md` - Only for major architecture/workflow changes
- `docs/ARCHITECTURE.md` - Only for new components/patterns

## Emergency Recovery

If lost or confused about documentation:

```bash
# Show documentation structure
tree docs/

# Show what to work on
cat memory/todo.md | head -50

# Show architecture overview
cat docs/ARCHITECTURE.md | head -100

# Show recent work
cat docs/SESSION_HISTORY.md | head -100

# Show build commands
grep -A 10 "## Build Commands" CLAUDE.md
```

## Documentation Maintenance

### Adding New Documentation
1. Create file in appropriate directory
2. Update this navigation guide
3. Update CLAUDE.md "Quick Navigation" if major doc
4. Commit with message: `docs: add [description]`

### Migrating Content
**From CLAUDE.md to dedicated docs:**
1. Identify historical/detailed content
2. Move to appropriate file (SESSION_HISTORY.md, ARCHITECTURE.md, specs/)
3. Replace in CLAUDE.md with pointer to new location
4. Commit with message: `docs: reorganize [description]`

### Archiving Old Content
**When content becomes outdated:**
1. Move to `docs/archive/` directory
2. Add "ARCHIVED" notice at top of file
3. Remove references from navigation
4. Commit with message: `docs: archive [description]`

---

**Last Updated**: 2025-10-19
**Purpose**: Help developers find information quickly
**Maintenance**: Update when adding major documentation
