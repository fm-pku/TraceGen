#include <stdio.h>

int m, n, k, n_;

int inner(int i, int j)
{
    return i - j < k && j - i < k;
}

int inner_or_bound(int i, int j)
{
    return i - j <= k && j - i <= k;
}

void print(FILE *f, int *s, int count, int i, int j)
{
     int c = 0, x = i, y = j;
     for (; c < count; c++)
     {
         if (s[c] == 0)
         {
             fprintf(f, "A%d ", x+1);
             x++;
         }
         else
         {
             fprintf(f, "B%d ", y+1);
             y++;
         }
     }
     fprintf(f, "\n");
}

int find_inner_trace(int s, int t, int i, int j)
{
    FILE *f1 = fopen("tset1", "w");
    int *stack = malloc((i+j-s-t)*sizeof(int));
    int count_0 = 0, count_1 = 0;
    int find_next = 0;
    int trace_count = 0;
    if (stack == NULL) exit(-1);
    
    do
    {
        if (find_next)
        {
            if (stack[count_0+count_1-1] == 0)
            {
                if (count_1 == j - t || !inner(s+count_0-1, t+count_1+1))
                    count_0--;
                else
                {
                    stack[count_0+count_1-1] = 1;
                    count_0--;
                    count_1++;
                    find_next = 0;
                }
            }
            else count_1--;
        }
        else
        {
            if (count_0 + count_1 == i + j - s - t - 1)
            {
                if (count_0 < i - s)
                {
                    stack[count_0 + count_1] = 0;
                    count_0++;
                }
                else
                {
                    stack[count_0 + count_1] = 1;
                    count_1++;
                }
            }
            else if (count_0 < i - s && inner(s+count_0+1, t+count_1))
            {
                stack[count_0+count_1] = 0;
                count_0++;
            }
            else if (count_1 < j - t && inner(s+count_0, t+count_1+1))
            {
                stack[count_0+count_1] = 1;
                count_1++;
            }
            else if (count_0 + count_1 == i + j - s - t)
            {
                print(f1, stack, count_0+count_1, s, t);
                find_next = 1;
                trace_count++;
            }
        }
    } while (count_0+count_1 > 0);
    fclose(f1);
    free(stack);
    return trace_count;
}

int find_trace(int i, int j, int s, int t)
{
    FILE *f2 = fopen("tset2", "w");
    int *stack = malloc((s+t-i-j)*sizeof(int));
    int count_0 = 0, count_1 = 0;
    int find_next = 0;
    int trace_count = 0;
    if (stack == NULL) exit(-1);
    
    do
    {
        if (find_next)
        {
            if (stack[count_0+count_1-1] == 0)
            {
                if (count_1 == t - j || !inner_or_bound(i+count_0-1, j+count_1+1))
                    count_0--;
                else
                {
                    stack[count_0+count_1-1] = 1;
                    count_0--;
                    count_1++;
                    find_next = 0;
                }
            }
            else count_1--;
        }
        else
        {
            if (count_0 < s - i && inner_or_bound(i+count_0+1, j+count_1))
            {
                stack[count_0+count_1] = 0;
                count_0++;
            }
            else if (count_1 < t - j && inner_or_bound(i+count_0, j+count_1+1))
            {
                stack[count_0+count_1] = 1;
                count_1++;
            }
            else if (count_0 + count_1 == s + t - i - j)
            {
                print(f2, stack, count_0+count_1, i, j);
                find_next = 1;
                trace_count++;
            }
        }
    } while (count_0+count_1 > 0);
    fclose(f2);
    free(stack);
    return trace_count;
}

void output(FILE *f, int x, int y)
{
    FILE *f1 = fopen("tset1", "r");
    FILE *f2 = fopen("tset2", "r");
    char c1[1024], c2[1024];
    int i, j, k;
    if (n_ == n)
    {
        for (i = 0; i < x; i++)
        {
            fclose(f2);
            f2 = fopen("tset2", "r");
            fgets(c1, 1024, f1);
            c1[strlen(c1)-1]='\0';
            for (j = 0; j < y; j++)
            {
                fgets(c2, 1024, f2);
                fprintf(f, c1);
                fprintf(f, c2);
            }
        }
    }
    else
    {
        for (i = 0; i < x; i++)
        {
            fclose(f2);
            f2 = fopen("tset2", "r");
            fgets(c1, 1024, f1);
            c1[strlen(c1)-1]='\0';
            for (j = 0; j < y; j++)
            {
                fgets(c2, 1024, f2);
                c2[strlen(c2)-1]='\0';
                fprintf(f, c1);
                fprintf(f, c2);
                for (k = n; k < n_; k++)
                    fprintf(f, "B%d ", k+1);
                fprintf(f, "\n");
            }
        }
    }
    fclose(f1);
    fclose(f2);
}

void enum_FSC_traces(int a, int b, int c)
{
    FILE *f = fopen("FSC.txt", "w");
    int i, j, x, y;
    if (a <= 0 || b <= 0 || c <= 0) exit(-1);
    if (a > b) {m = b; n = a; k = c;}
        else {m = a; n = b; k = c;}
    n_ = n;
    if (n > m + k) n = m + k;
    for (i = k, j = 0; i <= m; i++, j++)
    {
        x = find_inner_trace(0, 0, i, j);
        y = find_trace(i, j, m, n);
        output(f, x, y);
    }
    for (i = 0, j = k; i <= m && j <= n; i++, j++)
    {
        x = find_inner_trace(0, 0, i, j);
        y = find_trace(i, j, m, n);
        output(f, x, y);
    }
    remove("tset1");
    remove("tset2");
    fclose(f);
    printf("Output to file: FSC.txt\n");
    system("pause");
}

int main()
{
    enum_FSC_traces(4, 12, 4);
    return 0;
}
