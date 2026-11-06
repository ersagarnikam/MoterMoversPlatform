import { api, type FileUpload } from './api'

/**
 * Sends a file straight to the file store with the link the API gives, so a film never passes through the API. XHR,
 * because fetch cannot report upload progress. The content page is the only one whose CSP allows this: reach it with a
 * full page load (RUNBOOK, File storage).
 */
export async function uploadFile(file: File, onProgress: (percent: number) => void): Promise<string> {
  const upload = await api.startUpload(file.type, file.size)
  await send(upload, file, onProgress)
  return upload.key
}

function send(upload: FileUpload, file: File, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const failed = () => reject(new Error('The file could not be sent. Please try again.'))
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', upload.url)
    xhr.setRequestHeader('Content-Type', upload.contentType)
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
    }
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : failed())
    xhr.onerror = failed
    xhr.onabort = failed
    xhr.send(file)
  })
}
