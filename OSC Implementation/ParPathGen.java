import java.io.*;

class ParPathGen
{
	private int m, n, K;
	private int[][][] pathCount;
	private Tree[][] t;
	
	ParPathGen(int i, int j, int k)
	{
		m = i;
		n = j;
		K = k;
		pathCount = new int[m+1][n+1][K+1]; // K <= m + n
		t = new Tree[m+1][n+1];
	}
	
	public static void main(String args[]) throws IOException
	{
		ParPathGen p = new ParPathGen(10, 8, 2);
		p.pathGen();
		p.printPath("path.txt");
		
	}
	
	private void pathGen()
	{
		pathInit();
		treeInit(t, 0, 0, 0);
		int i, j;
		boolean flag = true;
		while (flag)
		{
			int i1, j1;
			for (i1 = 0; i1 <= m; i1++)
				for (j1 = 0; j1 <= n; j1++)
					t[i1][j1].depthAdjust();
			flag = false;
			for (i = 0; i <= m; i++)//
				for (j = 0; j <= n; j++)//
				{
					Tree t_in;
					if (i > 0 && j > 0)
						t_in = t[i][j-1].subtree(true).add(t[i-1][j].subtree(false));
					else if (i > 0)
						t_in = t[i-1][j].subtree(false);
					else if (j > 0)
						t_in = t[i][j-1].subtree(true);
					else
						continue;
					Tree t_out = t[i][j].del_highest_level(K);
					if ((i==0&&j==0)||t_in.equal(t_out))
						continue;
					flag = true;
					if (t_out.less(t_in))
					{
						add_out(t[i][j], t_in, K, i, j);
					}
					else if (t_in.less(t_out))
					{
						Tree[] in_trees = new Tree[2];
						if (j > 0) in_trees[0] = t[i][j-1].subtree(true);
						else in_trees[0] = null;
						if (i > 0) in_trees[1] = t[i-1][j].subtree(false);
						else in_trees[1] = null;
						add_in(t[i][j], in_trees, K);
						if (j > 0)
						{
							t[i][j-1].left = t[i][j-1].depth_inc(in_trees[0]);
							t[i][j-1].value = t[i][j-1].left.value + t[i][j-1].right.value;
						}
						if (i > 0)
						{
							t[i-1][j].right = t[i-1][j].depth_inc(in_trees[1]);
							t[i-1][j].value = t[i-1][j].left.value + t[i-1][j].right.value;
						}
					}
					else
					{
						Tree[] in_trees = new Tree[2];
						if (j > 0) in_trees[0] = t[i][j-1].subtree(true);
						else in_trees[0] = null;
						if (i > 0) in_trees[1] = t[i-1][j].subtree(false);
						else in_trees[1] = null;
						add_both(t[i][j], in_trees, K, i, j);
						if (j > 0)
						{
							t[i][j-1].left = t[i][j-1].depth_inc(in_trees[0]);
							t[i][j-1].value = t[i][j-1].left.value + t[i][j-1].right.value;
						}
						if (i > 0)
						{
							t[i-1][j].right = t[i-1][j].depth_inc(in_trees[1]);
							t[i-1][j].value = t[i-1][j].left.value + t[i-1][j].right.value;
						}
					}
				}
		}
	}
	
	private void pathInit()
	{
		int i, j, k;
		for (i = 0; i <= m; i++)
			for (j = 0; j <= n; j++)
				pathCount[i][j][0] = 1;

		for (k = 1; k <= K; k++)
			for (i = 0; i <= m; i++)
				for (j = 0; j <= n; j++)
					pathCount[i][j][k] = pathCount(i+1, j, k-1) + pathCount(i, j+1, k-1);
	}
	
	private int pathCount(int i, int j, int k)
	{
		if (0 <= i && i <= m)
			if (0 <= j && j <= n)
				return pathCount[i][j][k];
		return 0;
	}
	
	private void treeInit(Tree[][] t, int x, int y, int z)
	{
		if (z == K+1) return;
		int i, j;
		Tree[][] t_left = new Tree[m+1][n+1], t_right = new Tree[m+1][n+1];
		treeInit(t_left, x, y+1, z+1);
		treeInit(t_right, x+1, y, z+1);
		for (i = 0; i <= m; i++)
			for (j = 0; j <= n; j++)
			{
				t[i][j] = new Tree(z, pathCount(i+x, j+y, K-z));
				t[i][j].left = t_left[i][j];
				t[i][j].right = t_right[i][j];
			}
	}
	
	private void add_out(Tree t_out, Tree t_in, int depth, int i, int j)
	{
		if (t_out.value == t_in.value) return;
		else if (depth > 1)
		{
			t_out.value = t_in.value;
			add_out(t_out.left, t_in.left, depth-1, i, j+1);
			add_out(t_out.right, t_in.right, depth-1, i+1, j);
		}
		else
		{
			if (i == m)
			{
				t_out.left.value += t_in.value - t_out.value;
				t_out.value = t_in.value;
			}
			else if (j == n)
			{
				t_out.right.value += t_in.value - t_out.value;
				t_out.value = t_in.value;
			}
			else if (i < m && j < n)
			{
				while (t_out.value < t_in.value)
				{
					t_out.value++;
					if (t_out.left.value*pathCount(i+1, j, 1)>t_out.right.value*pathCount(i, j+1, 1))
						//according to the relationship of current proportion of $t[i][j].down$ and $t[i][j].right$,
						//and the proportion of the initial value of them.
						t_out.right.value++;
					else t_out.left.value++;
				}
			}
			else
			{
				t_out.value = t_in.value;
				t_out.left.value = t_in.value - t_out.right.value;
			}
		}
		return;
	}
	
	private void add_in(Tree t_out, Tree[] in_trees, int depth)
	{
		if (in_trees[0] == null)
		{
			in_trees[1] = t_out.del_highest_level(depth);
			return;
		}
		if (in_trees[1] == null)
		{
			in_trees[0] = t_out.del_highest_level(depth);
			return;
		}
		if (t_out.value == in_trees[0].value+in_trees[1].value) return;
		else if (depth == 1)
		{
			while (t_out.value > in_trees[0].value+in_trees[1].value)
			{
				if (in_trees[0].value < in_trees[1].value)
					in_trees[0].value++;
				else in_trees[1].value++;
			}
			return;
		}
		else
		{
			Tree[] left_tree = new Tree[2], right_tree = new Tree[2];
			left_tree[0] = in_trees[0].left;
			left_tree[1] = in_trees[1].left;
			right_tree[0] = in_trees[0].right;
			right_tree[1] = in_trees[1].right;
			add_in(t_out.left, left_tree, K-1);
			add_in(t_out.right, right_tree, K-1);
			in_trees[0].left = left_tree[0];
			in_trees[1].left = left_tree[1];
			in_trees[0].right = right_tree[0];
			in_trees[1].right = right_tree[1];
			in_trees[0].value = in_trees[0].left.value + in_trees[0].right.value;
			in_trees[1].value = in_trees[1].left.value + in_trees[1].right.value;
			return;
		}
	}
	
	private void add_both(Tree t_out, Tree[] in_trees, int depth, int i, int j)
	{
		if (t_out.del_highest_level(depth).lessequal((in_trees[0].add(in_trees[1]))))
		{
			add_out(t_out, (in_trees[0].add(in_trees[1])), depth, i, j);
		}
		else if ((in_trees[0].add(in_trees[1])).lessequal(t_out.del_highest_level(depth)))
		{
			add_in(t_out, in_trees, depth);
		}
		else
		{
			Tree[] left_tree = new Tree[2], right_tree = new Tree[2];
			left_tree[0] = in_trees[0].left;
			left_tree[1] = in_trees[1].left;
			right_tree[0] = in_trees[0].right;
			right_tree[1] = in_trees[1].right;
			add_both(t_out.left, left_tree, depth-1, i, j+1);
			add_both(t_out.right, right_tree, depth-1, i+1, j);
			in_trees[0].left = left_tree[0];
			in_trees[1].left = left_tree[1];
			in_trees[0].right = right_tree[0];
			in_trees[1].right = right_tree[1];
			in_trees[0].value = in_trees[0].left.value + in_trees[0].right.value;
			in_trees[1].value = in_trees[1].left.value + in_trees[1].right.value;
			t_out.value = t_out.left.value + t_out.right.value;
		}
		return;
	}
	
	private void printPath(String fileName) throws IOException
	{
		boolean[][] path = new boolean[t[0][0].value][m+n];
		int count = 0, length = 0;
		while (t[0][0].value > 0)
		{
			length = 0;
			Tree temp = t[0][0];
			while (length < K)
			{
				if (temp.left.value > 0)
				{
					path[count][length] = true;
					temp = temp.left;
				}
				else
				{
					path[count][length] = false;
					temp = temp.right;
				}
				length++;
			}
			while (length < m+n)
			{
				temp = find(path[count], length);
				path[count][length] = (temp.left.value > 0);
				length++;
			}
			delete(path[count]);
			count++;
		}
		FileOutputStream output = new FileOutputStream(fileName);
		int num, len, countA, countB;
		for (num = 0; num < count; num++)
		{
			countA = 1; countB = 1;
			for (len = 0; len < m+n; len++)
			{
				if (path[num][len])
				{
					output.write(("A"+countA+" ").getBytes());
					countA++;
				}
				else
				{
					output.write(("B"+countB+" ").getBytes());
					countB++;
				}
			}
			output.write(System.getProperty("line.separator").getBytes());
		}
	}
	
	private Tree find(boolean[] path, int length)
	{
		int i = 0, j = 0, num = 0;
		for (; num < length - K + 1; num++)
		{
			if (path[num]) j++;
			else i++;
		}
		Tree temp = t[i][j];
		for (; num < length; num++)
		{
			if (path[num]) temp = temp.left;
			else temp = temp.right;
		}
		return temp;
	}
	
	private void delete(boolean[] path)
	{
		int num, length, i, j, k;
		Tree temp;
		for (num = 0; num < m + n; num++)
		{
			i = 0; j = 0;
			for (k = 0; k < num; k++)
			{
				if (path[k]) j++;
				else i++;
			}
			temp = t[i][j];
			temp.value--;
			for(length = 0; length < K; length++)
			{
				if (path[num])
					temp = temp.left;
				else
					temp = temp.right;
				temp.value--;
			}
		}
	}
	
	private int max(int a, int b)
	{
		if (a > b) return a;
		else return b;
	}
	
	private int min(int a, int b)
	{
		if (a < b) return a;
		else return b;
	}
}