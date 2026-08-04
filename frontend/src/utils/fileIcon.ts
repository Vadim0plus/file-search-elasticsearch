const ICONS_BY_EXTENSION: Record<string, string> = {
  pdf: '📕',
  doc: '📘',
  docx: '📘',
  xls: '📗',
  xlsx: '📗',
  csv: '📊',
  ppt: '📙',
  pptx: '📙',
  txt: '📄',
  md: '📄',
  log: '📄',
  json: '🧾',
  xml: '🧾',
  yaml: '🧾',
  yml: '🧾',
  png: '🖼',
  jpg: '🖼',
  jpeg: '🖼',
  gif: '🖼',
  svg: '🖼',
  webp: '🖼',
  js: '💻',
  ts: '💻',
  tsx: '💻',
  jsx: '💻',
  java: '💻',
  py: '💻',
  html: '💻',
  css: '💻',
  zip: '🗜',
  tar: '🗜',
  gz: '🗜',
}

const DEFAULT_ICON = '📁'

export function getFileIcon(extension: string): string {
  return ICONS_BY_EXTENSION[extension.toLowerCase()] ?? DEFAULT_ICON
}
