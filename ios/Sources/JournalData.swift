import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct Measurement: Codable, Hashable {
    var takenAt: Int64
    var sys: Int
    var dia: Int
    var pulse: Int?
    var arm = "left"
    var context = "rest"
    var valid = true
    var issues = ""
    var note = ""
    var bucket = "rest"
    var tags: [String] = []

    var date: Date { Date(timeIntervalSince1970: Double(takenAt) / 1_000) }

    var isWellFormed: Bool {
        takenAt > 0 && (60...260).contains(sys) && (30...160).contains(dia)
            && (pulse.map { (30...220).contains($0) } ?? true)
    }

    init(takenAt: Int64, sys: Int, dia: Int, pulse: Int?, note: String) {
        self.takenAt = takenAt
        self.sys = sys
        self.dia = dia
        self.pulse = pulse
        self.note = note
    }

    private enum CodingKeys: String, CodingKey {
        case takenAt, sys, dia, pulse, arm, context, valid, issues, note, bucket, tags
    }

    init(from decoder: Decoder) throws {
        let row = try decoder.container(keyedBy: CodingKeys.self)
        takenAt = try row.decode(Int64.self, forKey: .takenAt)
        sys = try row.decode(Int.self, forKey: .sys)
        dia = try row.decode(Int.self, forKey: .dia)
        pulse = try row.decodeIfPresent(Int.self, forKey: .pulse)
        arm = try row.decodeIfPresent(String.self, forKey: .arm) ?? "left"
        context = try row.decodeIfPresent(String.self, forKey: .context) ?? "rest"
        valid = try row.decodeIfPresent(Bool.self, forKey: .valid) ?? true
        issues = try row.decodeIfPresent(String.self, forKey: .issues) ?? ""
        note = try row.decodeIfPresent(String.self, forKey: .note) ?? ""
        bucket = try row.decodeIfPresent(String.self, forKey: .bucket) ?? "rest"
        tags = try row.decodeIfPresent([String].self, forKey: .tags) ?? []
    }
}

struct JournalFile: Codable, FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    static let formatID = "chronic-notebook-measurements"

    var format = formatID
    var version = 1
    var exportedAt = Int64(Date().timeIntervalSince1970 * 1_000)
    var measurements: [Measurement]

    init(measurements: [Measurement]) {
        self.measurements = measurements
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw JournalError.invalidFile }
        self = try Self.decode(data)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: try JSONEncoder().encode(self))
    }

    static func decode(_ data: Data) throws -> Self {
        let file = try JSONDecoder().decode(Self.self, from: data)
        guard file.format == formatID, file.version == 1,
              file.measurements.allSatisfy(\.isWellFormed) else { throw JournalError.invalidFile }
        return file
    }
}

enum JournalError: LocalizedError {
    case invalidFile
    case storageUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidFile: return "Файл не соответствует формату замеров дневника."
        case .storageUnavailable: return "Хранилище недоступно. Закройте и откройте приложение повторно."
        }
    }
}

@MainActor final class JournalStore: ObservableObject {
    @Published private(set) var rows: [Measurement] = []
    @Published private(set) var loadProblem: String?
    private var file: URL?

    init() {
        do {
            let folder = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                                     appropriateFor: nil, create: true)
            let storedFile = folder.appendingPathComponent("measurements.json")
            if FileManager.default.fileExists(atPath: storedFile.path) {
                rows = try JournalFile.decode(Data(contentsOf: storedFile)).measurements.sorted { $0.takenAt > $1.takenAt }
            }
            file = storedFile
        } catch {
            loadProblem = "Не удалось прочитать локальную историю. Исходный файл сохранён."
        }
    }

    @discardableResult
    func insert(_ records: [Measurement]) throws -> Int {
        guard let file else { throw JournalError.storageUnavailable }
        guard records.allSatisfy(\.isWellFormed) else { throw JournalError.invalidFile }
        var seen = Set(rows)
        let new = records.filter { seen.insert($0).inserted }
        let merged = (rows + new).sorted { $0.takenAt > $1.takenAt }
        let data = try JSONEncoder().encode(JournalFile(measurements: merged))
        try data.write(to: file, options: [.atomic, .completeFileProtection])
        rows = merged
        return new.count
    }
}
