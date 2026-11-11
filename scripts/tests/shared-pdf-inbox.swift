import Foundation

@main struct SharedPdfChecks {
    static func main() throws {
        let folder=FileManager.default.temporaryDirectory.appendingPathComponent("struct-share-check-"+UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:folder) }
        let now=Date()
        let pdf=Data("%PDF-1.7 synthetic fixture\n%%EOF".utf8)
        do { try SharedPdfInbox.validate(Data("not a pdf".utf8));fatalError("Non-PDF accepted") } catch SharedPdfError.notPDF {}
        do { try SharedPdfInbox.validate(Data(repeating:0,count:SharedPdfInbox.maximumBytes+1));fatalError("Oversized PDF accepted") } catch SharedPdfError.tooLarge {}
        try SharedPdfInbox.stage(pdf,fileName:"../../synthetic.pdf",directory:folder,now:now)
        let first=try SharedPdfInbox.pending(directory:folder,now:now)!
        precondition(first.fileName=="synthetic.pdf")
        do { try SharedPdfInbox.stage(pdf,fileName:"second.pdf",directory:folder,now:now);fatalError("Pending import overwritten") } catch SharedPdfError.waiting {}
        try SharedPdfInbox.remove(delivery:"wrong-delivery",directory:folder)
        let stillWaiting = try SharedPdfInbox.pending(directory:folder,now:now)
        precondition(stillWaiting?.delivery==first.delivery)
        try SharedPdfInbox.remove(delivery:first.delivery,directory:folder)
        let emptyAfterRemoval = try SharedPdfInbox.pending(directory:folder,now:now)
        precondition(emptyAfterRemoval==nil)
        try SharedPdfInbox.stage(pdf,fileName:"expired.pdf",directory:folder,now:now.addingTimeInterval(-86401))
        let emptyAfterExpiry = try SharedPdfInbox.pending(directory:folder,now:now)
        precondition(emptyAfterExpiry==nil)
        precondition(!FileManager.default.fileExists(atPath:folder.appendingPathComponent("pending.pdf").path))
        print("PDF share checks passed: header/size validation, safe name, no overwrite, matching delivery removal, expiry cleanup.")
    }
}
