import Pow
import SwiftUI
import UniformTypeIdentifiers

@main @MainActor struct ChronicNotebookApp: App {
    var body: some Scene {
        WindowGroup { JournalView() }
    }
}

@MainActor struct JournalView: View {
    @StateObject private var store = JournalStore()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showEntry = false
    @State private var showImport = false
    @State private var showExport = false
    @State private var recentSave = 0
    @State private var message: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    latest
                    if let problem = store.loadProblem {
                        Text(problem).foregroundStyle(.secondary)
                    }
                    Text("История замеров").font(.headline)
                    if store.rows.isEmpty {
                        Text("Здесь появятся сохранённые замеры.").foregroundStyle(.secondary)
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(store.rows, id: \.self) { row in
                                VStack(alignment: .leading, spacing: 5) {
                                    Text("\(row.sys) / \(row.dia) мм рт. ст.").font(.headline)
                                    Text(row.date.formatted(date: .abbreviated, time: .shortened))
                                        .font(.subheadline).foregroundStyle(.secondary)
                                    if let pulse = row.pulse { Text("Пульс \(pulse) уд/мин").font(.subheadline) }
                                    if !row.note.isEmpty { Text(row.note).font(.subheadline) }
                                    if !row.tags.isEmpty {
                                        Text(row.tags.joined(separator: ", ")).font(.footnote).foregroundStyle(.secondary)
                                    }
                                    if !row.valid && !row.issues.isEmpty {
                                        Text(row.issues).font(.footnote).foregroundStyle(.secondary)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
                            }
                        }
                    }
                }
                .padding(18)
            }
            .background {
                LinearGradient(colors: [Color.accentColor.opacity(0.12), Color(uiColor: .systemBackground)],
                               startPoint: .topTrailing, endPoint: .center).ignoresSafeArea()
            }
            .navigationTitle("Дневник давления")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button("Импорт JSON") { showImport = true }
                        Button("Экспорт JSON") { showExport = true }.disabled(store.rows.isEmpty)
                    } label: {
                        Label("Файлы дневника", systemImage: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showEntry) {
                EntryView { measurement in
                    try store.insert([measurement])
                    recentSave += 1
                }
            }
            .fileImporter(isPresented: $showImport, allowedContentTypes: [.json]) { result in
                do {
                    let url = try result.get()
                    let access = url.startAccessingSecurityScopedResource()
                    defer { if access { url.stopAccessingSecurityScopedResource() } }
                    let file = try JournalFile.decode(Data(contentsOf: url))
                    let count = try store.insert(file.measurements)
                    message = "Добавлено: \(count). Уже были в дневнике: \(file.measurements.count - count)."
                } catch { message = error.localizedDescription }
            }
            .fileExporter(isPresented: $showExport, document: JournalFile(measurements: store.rows),
                          contentType: .json, defaultFilename: "chronic-notebook-measurements") { result in
                if case let .failure(error) = result { message = error.localizedDescription }
            }
            .alert("Дневник давления", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("ОК", role: .cancel) { message = nil }
            } message: {
                Text(message ?? "")
            }
        }
    }

    private var latest: some View {
        VStack(alignment: .leading, spacing: 11) {
            Text("Последний замер").font(.headline)
            if let row = store.rows.first {
                Text("\(row.sys) / \(row.dia)").font(.largeTitle.bold()).monospacedDigit()
                Text(row.pulse.map { "мм рт. ст.  ·  Пульс \($0) уд/мин" } ?? "мм рт. ст.")
                    .foregroundStyle(.secondary)
                Text(row.date.formatted(date: .abbreviated, time: .shortened))
                    .font(.footnote).foregroundStyle(.secondary)
            } else {
                Text("Записей пока нет").font(.title3.weight(.semibold))
            }
            Button { showEntry = true } label: {
                Label("Добавить замер", systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)
            .disabled(store.loadProblem != nil)
            .changeEffect(.pulse(shape: Capsule(), drawingMode: .stroke, count: 1), value: recentSave,
                          isEnabled: !reduceMotion)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28))
        .overlay { RoundedRectangle(cornerRadius: 28).strokeBorder(.primary.opacity(0.10)) }
    }
}

private struct EntryView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var sys = ""
    @State private var dia = ""
    @State private var pulse = ""
    @State private var note = ""
    @State private var date = Date()
    @State private var problem: String?
    let save: (Measurement) throws -> Void

    private var entry: Measurement? {
        guard let systolic = Int(sys), let diastolic = Int(dia), pulse.isEmpty || Int(pulse) != nil else { return nil }
        let value = Measurement(takenAt: Int64(date.timeIntervalSince1970 * 1_000), sys: systolic, dia: diastolic,
                                pulse: Int(pulse), note: note)
        return value.isWellFormed ? value : nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Давление, мм рт. ст.") {
                    TextField("Верхнее", text: $sys).keyboardType(.numberPad)
                    TextField("Нижнее", text: $dia).keyboardType(.numberPad)
                    TextField("Пульс, уд/мин (необязательно)", text: $pulse).keyboardType(.numberPad)
                }
                Section("Детали") {
                    DatePicker("Дата и время", selection: $date, in: ...Date())
                    TextField("Заметка", text: $note, axis: .vertical).lineLimit(2...6)
                }
                if let problem { Text(problem).foregroundStyle(.red) }
            }
            .navigationTitle("Новый замер")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        guard let entry else { return }
                        do { try save(entry); dismiss() } catch { problem = error.localizedDescription }
                    }
                    .disabled(entry == nil)
                }
            }
        }
    }
}
