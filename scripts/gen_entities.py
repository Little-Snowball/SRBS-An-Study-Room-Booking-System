#!/usr/bin/env python3
"""Generate plain Java entity classes (no Lombok) from a compact field spec.
Keeps entity definitions consistent with the implementation manual chapter 7.2."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "backend", "src", "main", "java", "edu", "campus", "studyroom")

# (class, package_suffix, [(field, type), ...])
ENTITIES = [
    ("AppUser", "user", [
        ("id", "Long"), ("account", "String"), ("passwordHash", "String"), ("name", "String"),
        ("college", "String"), ("phone", "String"), ("role", "UserRole"), ("status", "UserStatus"),
        ("blocked", "Boolean"), ("mustChangePassword", "Boolean"), ("authVersion", "Integer"),
        ("createdAt", "LocalDateTime"), ("updatedAt", "LocalDateTime"),
    ]),
    ("AuthSession", "auth", [
        ("id", "String"), ("userId", "Long"), ("authVersion", "Integer"),
        ("expiresAt", "LocalDateTime"), ("revokedAt", "LocalDateTime"), ("createdAt", "LocalDateTime"),
    ]),
    ("CommandDedup", "common", [
        ("id", "Long"), ("userId", "Long"), ("commandType", "String"), ("requestKey", "String"),
        ("requestHash", "String"), ("resultType", "String"), ("resultId", "String"),
        ("createdAt", "LocalDateTime"),
    ]),
    ("CreditAccount", "credit", [
        ("userId", "Long"), ("score", "Integer"), ("version", "Long"), ("updatedAt", "LocalDateTime"),
    ]),
    ("CreditLog", "credit", [
        ("id", "Long"), ("userId", "Long"), ("reservationId", "Long"), ("violationId", "Long"),
        ("eventKey", "String"), ("type", "CreditLogType"), ("requestedDelta", "Integer"),
        ("actualDelta", "Integer"), ("cappedDelta", "Integer"), ("balanceBefore", "Integer"),
        ("balanceAfter", "Integer"), ("sourceLogId", "Long"), ("reason", "String"),
        ("operatorId", "Long"), ("createdAt", "LocalDateTime"),
    ]),
    ("StudyRoom", "resource", [
        ("id", "Long"), ("code", "String"), ("name", "String"), ("campus", "String"),
        ("building", "String"), ("floorNo", "Integer"), ("openTime", "LocalTime"),
        ("closeTime", "LocalTime"), ("rowsCount", "Integer"), ("colsCount", "Integer"),
        ("checkinCodeHash", "String"), ("status", "RoomStatus"), ("version", "Long"),
        ("createdAt", "LocalDateTime"), ("updatedAt", "LocalDateTime"),
    ]),
    ("Seat", "resource", [
        ("id", "Long"), ("roomId", "Long"), ("code", "String"), ("rowNo", "Integer"),
        ("colNo", "Integer"), ("hasPower", "Boolean"), ("nearWindow", "Boolean"),
        ("quietZone", "Boolean"), ("status", "SeatStatus"), ("version", "Long"),
        ("createdAt", "LocalDateTime"), ("updatedAt", "LocalDateTime"),
    ]),
    ("ResourceStatusEvent", "resource", [
        ("id", "Long"), ("resourceType", "ResourceType"), ("eventType", "ResourceEventType"),
        ("roomId", "Long"), ("seatId", "Long"), ("afterSnapshot", "String"),
        ("orderPolicy", "OrderPolicy"), ("effectiveAt", "LocalDateTime"),
        ("operatorId", "Long"), ("createdAt", "LocalDateTime"),
    ]),
    ("RetainedService", "resource", [
        ("id", "Long"), ("reservationId", "Long"), ("seatId", "Long"),
        ("fromAt", "LocalDateTime"), ("toAt", "LocalDateTime"), ("causeEventId", "Long"),
        ("createdAt", "LocalDateTime"),
    ]),
    ("RuleVersion", "rule", [
        ("id", "Long"), ("versionNo", "Integer"), ("configJson", "String"),
        ("source", "RuleSource"), ("createdBy", "Long"), ("createdAt", "LocalDateTime"),
    ]),
    ("BusinessGuard", "rule", [
        ("id", "Integer"), ("activeRuleId", "Long"), ("updatedAt", "LocalDateTime"),
    ]),
    ("Reservation", "reservation", [
        ("id", "Long"), ("orderNo", "String"), ("userId", "Long"), ("roomId", "Long"),
        ("seatId", "Long"), ("roomNameSnapshot", "String"), ("seatCodeSnapshot", "String"),
        ("startAt", "LocalDateTime"), ("endAt", "LocalDateTime"), ("status", "ReservationStatus"),
        ("ruleVersionId", "Long"), ("ruleSnapshot", "String"), ("checkinDueAt", "LocalDateTime"),
        ("cancelDueAt", "LocalDateTime"), ("checkInAt", "LocalDateTime"),
        ("lastLeaveAt", "LocalDateTime"), ("leaveDueAt", "LocalDateTime"),
        ("finishedAt", "LocalDateTime"), ("leaveCount", "Integer"),
        ("terminationReason", "TerminationReason"), ("version", "Long"),
        ("createdAt", "LocalDateTime"), ("updatedAt", "LocalDateTime"),
    ]),
    ("AttendanceEvent", "reservation", [
        ("id", "Long"), ("reservationId", "Long"), ("eventType", "AttendanceEventType"),
        ("eventAt", "LocalDateTime"), ("operatorId", "Long"), ("requestKey", "String"),
        ("createdAt", "LocalDateTime"),
    ]),
    ("SettlementResult", "settlement", [
        ("reservationId", "Long"), ("terminalEventKey", "String"), ("reason", "TerminationReason"),
        ("finishedAt", "LocalDateTime"), ("eligibleSeconds", "Long"),
        ("rewardRequested", "Integer"), ("rewardActual", "Integer"),
        ("creditLogId", "Long"), ("createdAt", "LocalDateTime"),
    ]),
    ("Violation", "settlement", [
        ("id", "Long"), ("reservationId", "Long"), ("userId", "Long"),
        ("reason", "ViolationReason"), ("status", "ViolationStatus"),
        ("eventAt", "LocalDateTime"), ("processedAt", "LocalDateTime"),
        ("notifiedAt", "LocalDateTime"), ("deductionLogId", "Long"),
        ("revokedAt", "LocalDateTime"), ("revokedBy", "Long"), ("createdAt", "LocalDateTime"),
    ]),
    ("Appeal", "appeal", [
        ("id", "Long"), ("violationId", "Long"), ("userId", "Long"), ("reason", "String"),
        ("status", "AppealStatus"), ("reviewerId", "Long"), ("reviewComment", "String"),
        ("submittedAt", "LocalDateTime"), ("reviewedAt", "LocalDateTime"), ("version", "Long"),
    ]),
    ("AppealFile", "appeal", [
        ("appealId", "Long"), ("fileId", "Long"),
    ]),
    ("FileObject", "file", [
        ("id", "Long"), ("ownerId", "Long"), ("storageKey", "String"),
        ("originalName", "String"), ("mimeType", "String"), ("sizeBytes", "Long"),
        ("sha256", "String"), ("state", "FileState"), ("createdAt", "LocalDateTime"),
    ]),
    ("Notification", "notification", [
        ("id", "Long"), ("userId", "Long"), ("eventKey", "String"), ("type", "String"),
        ("title", "String"), ("content", "String"), ("reservationId", "Long"),
        ("violationId", "Long"), ("readAt", "LocalDateTime"), ("publishedAt", "LocalDateTime"),
    ]),
    ("Notice", "notice", [
        ("id", "Long"), ("title", "String"), ("content", "String"), ("pinned", "Boolean"),
        ("status", "NoticeStatus"), ("publishedAt", "LocalDateTime"), ("createdBy", "Long"),
        ("version", "Long"), ("createdAt", "LocalDateTime"), ("updatedAt", "LocalDateTime"),
    ]),
    ("AuditLog", "audit", [
        ("id", "Long"), ("actorId", "Long"), ("action", "String"), ("targetType", "String"),
        ("targetId", "String"), ("traceId", "String"), ("summary", "String"),
        ("createdAt", "LocalDateTime"),
    ]),
]

JAVA_TYPES = {"LocalDateTime", "LocalTime"}


def cap(name):
    return name[0].upper() + name[1:]


def gen(cls, pkg, fields):
    imports = set()
    for f, t in fields:
        if t in JAVA_TYPES:
            imports.add(f"import java.time.{t};")
        elif t not in ("String", "Long", "Integer", "Boolean"):
            imports.add(f"import edu.campus.studyroom.{pkg}.domain.{t};")
    lines = [f"package edu.campus.studyroom.{pkg}.domain;", ""]
    lines += sorted(imports)
    lines += ["", f"/** Entity {cls} (table: {to_snake(cls)}). Plain Java class per implementation manual. */",
              f"public class {cls} {{", ""]
    for f, t in fields:
        lines.append(f"    private {t} {f};")
    lines.append("")
    for f, t in fields:
        g = "get" + cap(f) if t != "Boolean" or f.lower().startswith("is") else "is" + cap(f)
        if t == "Boolean" and not f.lower().startswith("is"):
            g = "is" + cap(f)
        lines += [f"    public {t} {g}() {{", f"        return {f};", "    }", ""]
        s = "set" + cap(f)
        lines += [f"    public void {s}({t} {f}) {{", f"        this.{f} = {f};", "    }", ""]
    lines.append("}")
    return "\n".join(lines) + "\n"


def to_snake(name):
    out = []
    for i, ch in enumerate(name):
        if ch.isupper() and i > 0:
            out.append("_")
        out.append(ch.lower())
    return "".join(out)


count = 0
for cls, pkg, fields in ENTITIES:
    path = os.path.join(BASE, pkg, "domain")
    os.makedirs(path, exist_ok=True)
    with open(os.path.join(path, cls + ".java"), "w", encoding="utf-8") as fh:
        fh.write(gen(cls, pkg, fields))
    count += 1
print(f"generated {count} entities")
