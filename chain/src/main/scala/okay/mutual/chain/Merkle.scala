package okay.mutual.chain

/**
 * A NODE'S HEAD OVER ALL ITS ACCOUNTS (specs/protocol.md "A node"): a Merkle tree whose leaves are the accounts'
 * heads, sorted by account. The root is one hash a node signs; a proof — the sibling hashes from a leaf up — shows a
 * wallet that its own chain's head is under that root, without the other accounts. A leaf and an inner node are
 * hashed apart (`leaf:`, `node:`), so one is never taken for the other; a level's odd last hash goes up as it is
 */
object Merkle:
  def leaf(account: String, head: String): String = Sha256.hex(s"leaf:$account:$head")
  private def inner(left: String, right: String): String = Sha256.hex(s"node:$left:$right")
  val empty: String = Sha256.hex("empty")

  private def up(level: Vector[String]): Vector[String] =
    level.grouped(2).map(p => if p.size == 2 then inner(p(0), p(1)) else p(0)).toVector

  /** the root over the accounts' heads (account → head) */
  def root(heads: Map[String, String]): String =
    var level = heads.toVector.sortBy(_._1).map(leaf)
    if level.isEmpty then empty else
      while level.size > 1 do level = up(level)
      level.head

  /** the proof of an account's head: each step the sibling's hash and whether it stands on the left */
  def proof(heads: Map[String, String], account: String): Option[Vector[(String, Boolean)]] =
    val sorted = heads.toVector.sortBy(_._1)
    val at = sorted.indexWhere(_._1 == account)
    if at < 0 then None else
      var level = sorted.map(leaf)
      var i = at
      var steps = Vector.empty[(String, Boolean)]
      while level.size > 1 do
        val sibling = if i % 2 == 0 then i + 1 else i - 1
        if sibling < level.size then steps :+= (level(sibling), sibling < i)
        level = up(level)
        i /= 2
      Some(steps)

  /** whether the proof leads from this account's head to the root */
  def holds(root: String, account: String, head: String, proof: Vector[(String, Boolean)]): Boolean =
    proof.foldLeft(leaf(account, head))((h, step) => if step._2 then inner(step._1, h) else inner(h, step._1)) == root
