/** 将后端菜单树拍平为节点数组（保留全部字段，剔除 children 引用） */
export function flattenMenuTree(tree: any[]): any[] {
  const flat: any[] = []
  const walk = (nodes: any[]) => {
    for (const n of nodes || []) {
      flat.push({ ...n, children: undefined })
      if (n.children && n.children.length) walk(n.children)
    }
  }
  walk(tree)
  return flat
}
